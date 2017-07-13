第9章 轻量级线程：协程
===

在常用的并发模型中，多进程、多线程、分布式是最普遍的，不过近些年来逐渐有一些语言以first-class或者library的形式提供对基于协程的并发模型的支持。其中比较典型的有Scheme、Lua、Python、Perl、Go等以first-class的方式提供对协程的支持。

同样地，Kotlin也支持协程。

本章我们主要介绍：

- 什么是协程
- 协程的用法实例
- 挂起函数
- 通道与管道
- 协程的实现原理
- coroutine库等

## 9.1 协程简介

从硬件发展来看，从最初的单核单CPU，到单核多CPU，多核多CPU，似乎已经到了极限了，但是单核CPU性能却还在不断提升。如果将程序分为IO密集型应用和CPU密集型应用，二者的发展历程大致如下：

> IO密集型应用: 多进程->多线程->事件驱动->协程

> CPU密集型应用:多进程-->多线程 


如果说多进程对于多CPU，多线程对应多核CPU，那么事件驱动和协程则是在充分挖掘不断提高性能的单核CPU的潜力。

常见的有性能瓶颈的API (例如网络 IO、文件 IO、CPU 或 GPU 密集型任务等)，要求调用者阻塞（blocking）直到它们完成才能进行下一步。后来，我们又使用异步回调的方式来实现非阻塞，但是异步回调代码写起来并不简单。

协程提供了一种避免阻塞线程并用更简单、更可控的操作替代线程阻塞的方法：协程挂起。


协程主要是让原来要使用“异步+回调方式”写出来的复杂代码, 简化成可以用看似同步的方式写出来（对线程的操作进一步抽象）。这样我们就可以按串行的思维模型去组织原本分散在不同上下文中的代码逻辑，而不需要去处理复杂的状态同步问题。

协程最早的描述是由Melvin Conway于1958年给出：“subroutines who act as the master program”(与主程序行为类似的子例程)。此后他又在博士论文中给出了如下定义：

>- 数据在后续调用中始终保持（ The values of data local to a coroutine persist between successive calls 协程的局部）

> - 当控制流程离开时，协程的执行被挂起，此后控制流程再次进入这个协程时，这个协程只应从上次离开挂起的地方继续 （The execution of a coroutine is suspended as control leaves it, only to carry on where it left off when control re-enters the coroutine at some later stage）。

协程的实现要维护一组局部状态，在重新进入协程前，保证这些状态不被改变，从而能顺利定位到之前的位置。

协程可以用来解决很多问题，比如nodejs的嵌套回调，Erlang以及Golang的并发模型实现等。


实质上，协程（coroutine）是一种用户态的轻量级线程。它由协程构建器（launch coroutine builder）启动。


下面我们通过代码实践来学习协程的相关内容。

### 9.1.1 搭建协程代码工程

首先，我们来新建一个Kotlin Gradle工程。生成标准gradle工程后，在配置文件build.gradle中，配置kotlinx-coroutines-core依赖：

添加 dependencies :
```
compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:0.16'
```
kotlinx-coroutines还提供了下面的模块：

```
compile group: 'org.jetbrains.kotlinx', name: 'kotlinx-coroutines-jdk8', version: '0.16'
compile group: 'org.jetbrains.kotlinx', name: 'kotlinx-coroutines-nio', version: '0.16'
compile group: 'org.jetbrains.kotlinx', name: 'kotlinx-coroutines-reactive', version: '0.16'
```



我们使用Kotlin最新的1.1.3-2 版本:

```
buildscript {
    ext.kotlin_version = '1.1.3-2'
    ...
    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}
```

其中，kotlin-gradle-plugin是Kotlin集成Gradle的插件。


另外，配置一下JCenter 的仓库:

```
repositories {
    jcenter()
}
```

### 9.1.2 简单协程示例

下面我们先来看一个简单的协程示例。

运行下面的代码：
```
    fun firstCoroutineDemo0() {
        launch(CommonPool) {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("Hello,")
        }
        println("World!")
        Thread.sleep(5000L)
    }
```
你将会发现输出：
```
World!
Hello,
```



上面的这段代码：
```
launch(CommonPool) {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("Hello,")
}
```
等价于：
```
launch(CommonPool, CoroutineStart.DEFAULT, {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("Hello, ")
})
```

### 9.1.3 launch函数

这个launch函数定义在kotlinx.coroutines.experimental下面。

```
public fun launch(
    context: CoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val newContext = newCoroutineContext(context)
    val coroutine = if (start.isLazy)
        LazyStandaloneCoroutine(newContext, block) else
        StandaloneCoroutine(newContext, active = true)
    coroutine.initParentJob(context[Job])
    start(block, coroutine, coroutine)
    return coroutine
}
```

launch函数有3个入参：context、start、block，这些函数参数分别说明如下：

|参数    |        说明          |
|--------|----------------|
|context |   协程上下文|
|start      |   协程启动选项| 
| block   |   协程真正要执行的代码块，必须是suspend修饰的挂起函数|

这个launch函数返回一个Job类型，Job是协程创建的后台任务的概念，它持有该协程的引用。Job接口实际上继承自CoroutineContext类型。一个Job有如下三种状态：

  | **State**                        | isActive | isCompleted |
  | -------------------------------- | ---------- | ------------- |
  | _New_ (optional initial state)  新建 （可选的初始状态） | `false`    | `false`       |
  | _Active_ (default initial state)  活动中（默认初始状态）| `true`     | `false`       |
  | _Completed_ (final state)  已结束（最终状态）  | `false`    | `true`        |

也就是说，launch函数它以非阻塞（non-blocking）当前线程的方式，启动一个新的协程后台任务，并返回一个Job类型的对象作为当前协程的引用。

另外，这里的delay()函数类似Thread.sleep()的功能，但更好的是：它不会阻塞线程，而只是挂起协程本身。当协程在等待时，线程将返回到池中, 当等待完成时, 协同将在池中的空闲线程上恢复。

### 9.1.4  CommonPool：共享线程池

我们再来看一下`launch(CommonPool) {...}`这段代码。

首先，这个CommonPool是代表共享线程池，它的主要作用是来调度计算密集型任务的协程的执行。它的实现使用的是java.util.concurrent包下面的API。它首先尝试创建一个`java.util.concurrent.ForkJoinPool`   （ForkJoinPool是一个可以执行ForkJoinTask的ExcuteService，它采用了work-stealing模式：所有在池中的线程尝试去执行其他线程创建的子任务，这样很少有线程处于空闲状态，更加高效）；如果不可用，就使用`java.util.concurrent.Executors`来创建一个普通的线程池：`Executors.newFixedThreadPool`。相关代码在kotlinx/coroutines/experimental/CommonPool.kt中：

```
    private fun createPool(): ExecutorService {
        val fjpClass = Try { Class.forName("java.util.concurrent.ForkJoinPool") }
            ?: return createPlainPool()
        if (!usePrivatePool) {
            Try { fjpClass.getMethod("commonPool")?.invoke(null) as? ExecutorService }
                ?.let { return it }
        }
        Try { fjpClass.getConstructor(Int::class.java).newInstance(defaultParallelism()) as? ExecutorService }
            ?. let { return it }
        return createPlainPool()
    }

    private fun createPlainPool(): ExecutorService {
        val threadId = AtomicInteger()
        return Executors.newFixedThreadPool(defaultParallelism()) {
            Thread(it, "CommonPool-worker-${threadId.incrementAndGet()}").apply { isDaemon = true }
        }
    }
```

这个CommonPool对象类是CoroutineContext的子类型。它们的类型集成层次结构如下：



![螢幕快照 2017-07-12 13.14.54.png](http://upload-images.jianshu.io/upload_images/1233356-a219643430e8f474.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

### 9.1.5 挂起函数

代码块中的`delay(3000L, TimeUnit.MILLISECONDS)`函数，是一个用suspend关键字修饰的函数，我们称之为挂起函数。挂起函数只能从协程代码内部调用，普通的非协程的代码不能调用。



挂起函数只允许由协程或者另外一个挂起函数里面调用, 例如我们在协程代码中调用一个挂起函数，代码示例如下：

```
    suspend fun runCoroutineDemo() {
        run(CommonPool) {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("suspend,")
        }
        println("runCoroutineDemo!")
        Thread.sleep(5000L)
    }

    fun callSuspendFun() {
        launch(CommonPool) {
            runCoroutineDemo()
        }
    }
```




如果我们用Java中的Thread类来写类似功能的代码，上面的代码可以写成这样：
```
    fun threadDemo0() {
        Thread({
            Thread.sleep(3000L)
            println("Hello,")
        }).start()

        println("World!")
        Thread.sleep(5000L)
    }
```
输出结果也是：

World!
Hello,



另外， 我们不能使用Thread来启动协程代码。例如下面的写法编译器会报错：

```
    /**
     * 错误反例：用线程调用协程 error
     */
    fun threadCoroutineDemo() {
        Thread({
            delay(3000L, TimeUnit.MILLISECONDS) // error, Suspend functions are only allowed to be called from a coroutine or another suspend function
            println("Hello,")
        })
        println("World!")
        Thread.sleep(5000L)
    }
```


## 9.2  桥接 阻塞和非阻塞

上面的例子中，我们给出的是使用非阻塞的delay函数，同时有使用了阻塞的Thread.sleep函数，这样代码写在一起可读性不是那么地好。让我们来使用纯的Kotlin的协程代码来实现上面的 _阻塞+非阻塞_ 的例子（不用Thread）。

### 9.2.1 runBlocking函数

Kotlin中提供了runBlocking函数来实现类似主协程的功能：

```
fun main(args: Array<String>) = runBlocking<Unit> {
    // 主协程
    println("${format(Date())}: T0")

    // 启动主协程
    launch(CommonPool) {
        //在common thread pool中创建协程
        println("${format(Date())}: T1")
        delay(3000L)
        println("${format(Date())}: T2 Hello,")
    }
    println("${format(Date())}: T3 World!") //  当子协程被delay，主协程仍然继续运行

    delay(5000L)

    println("${format(Date())}: T4")
}

```

运行结果：
```
14:37:59.640: T0
14:37:59.721: T1
14:37:59.721: T3 World!
14:38:02.763: T2 Hello,
14:38:04.738: T4
```

可以发现，运行结果跟之前的是一样的，但是我们没有使用Thread.sleep，我们只使用了非阻塞的delay函数。如果main函数不加 `= runBlocking<Unit>` , 那么我们是不能在main函数体内调用delay(5000L)的。

如果这个阻塞的线程被中断，runBlocking抛出InterruptedException异常。

该runBlocking函数不是用来当做普通协程函数使用的，它的设计主要是用来桥接普通阻塞代码和挂起风格的（suspending style）的非阻塞代码的, 例如用在 `main` 函数中，或者用于测试用例代码中。
 
```
@RunWith(JUnit4::class)
class RunBlockingTest {

    @Test fun testRunBlocking() = runBlocking<Unit> {
        // 这样我们就可以在这里调用任何suspend fun了
        launch(CommonPool) {
            delay(3000L)
        }
        delay(5000L)
    }
}
```

## 9.3 等待一个任务执行完毕

我们先来看一段代码：
```
    fun firstCoroutineDemo() {
        launch(CommonPool) {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("[firstCoroutineDemo] Hello, 1")
        }

        launch(CommonPool, CoroutineStart.DEFAULT, {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("[firstCoroutineDemo] Hello, 2")
        })
        println("[firstCoroutineDemo] World!")
    }
```

运行这段代码，我们会发现只输出：
```
[firstCoroutineDemo] World!
```

这是为什么？

为了弄清上面的代码执行的内部过程，我们打印一些日志看下：
```
   fun testJoinCoroutine() = runBlocking<Unit> {
        // Start a coroutine
        val c1 = launch(CommonPool) {
            println("C1 Thread: ${Thread.currentThread()}")
            println("C1 Start")
            delay(3000L)
            println("C1 World! 1")
        }

        val c2 = launch(CommonPool) {
            println("C2 Thread: ${Thread.currentThread()}")
            println("C2 Start")
            delay(5000L)
            println("C2 World! 2")
        }

        println("Main Thread: ${Thread.currentThread()}")
        println("Hello,")
        println("Hi,")
        println("c1 is active: ${c1.isActive}  ${c1.isCompleted}")
        println("c2 is active: ${c2.isActive}  ${c2.isCompleted}")

    }
```
再次运行：

```
C1 Thread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
C1 Start
C2 Thread: Thread[ForkJoinPool.commonPool-worker-2,5,main]
C2 Start
Main Thread: Thread[main,5,main]
Hello,
Hi,
c1 is active: true  false
c2 is active: true  false
```

我们可以看到，这里的C1、C2代码也开始执行了，使用的是`ForkJoinPool.commonPool-worker`线程池中的worker线程。但是，我们在代码执行到最后打印出这两个协程的状态isCompleted都是false，这表明我们的C1、C2的代码，在Main Thread结束的时刻（此时的运行main函数的Java进程也退出了），还没有执行完毕，然后就跟着主线程一起退出结束了。

所以我们可以得出结论：运行 main () 函数的主线程， 必须要等到我们的协程完成之前结束 , 否则我们的程序在 打印Hello, 1和Hello, 2之前就直接结束掉了。

我们怎样让这两个协程参与到主线程的时间顺序里呢？我们可以使用`join`, 让主线程一直等到当前协程执行完毕再结束, 例如下面的这段代码

```
    fun testJoinCoroutine() = runBlocking<Unit> {
        // Start a coroutine
        val c1 = launch(CommonPool) {
            println("C1 Thread: ${Thread.currentThread()}")
            println("C1 Start")
            delay(3000L)
            println("C1 World! 1")
        }

        val c2 = launch(CommonPool) {
            println("C2 Thread: ${Thread.currentThread()}")
            println("C2 Start")
            delay(5000L)
            println("C2 World! 2")
        }

        println("Main Thread: ${Thread.currentThread()}")
        println("Hello,")

        println("c1 is active: ${c1.isActive}  isCompleted: ${c1.isCompleted}")
        println("c2 is active: ${c2.isActive}  isCompleted: ${c2.isCompleted}")

        c1.join() // the main thread will wait until child coroutine completes
        println("Hi,")
        println("c1 is active: ${c1.isActive}  isCompleted: ${c1.isCompleted}")
        println("c2 is active: ${c2.isActive}  isCompleted: ${c2.isCompleted}")
        c2.join() // the main thread will wait until child coroutine completes
        println("c1 is active: ${c1.isActive}  isCompleted: ${c1.isCompleted}")
        println("c2 is active: ${c2.isActive}  isCompleted: ${c2.isCompleted}")
    }
```



将会输出：

```
C1 Thread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
C1 Start
C2 Thread: Thread[ForkJoinPool.commonPool-worker-2,5,main]
C2 Start
Main Thread: Thread[main,5,main]
Hello,
c1 is active: true  isCompleted: false
c2 is active: true  isCompleted: false
C1 World! 1
Hi,
c1 is active: false  isCompleted: true
c2 is active: true  isCompleted: false
C2 World! 2
c1 is active: false  isCompleted: true
c2 is active: false  isCompleted: true

```


通常，良好的代码风格我们会把一个单独的逻辑放到一个独立的函数中，我们可以重构上面的代码如下：

```
    fun testJoinCoroutine2() = runBlocking<Unit> {
        // Start a coroutine
        val c1 = launch(CommonPool) {
            fc1()
        }

        val c2 = launch(CommonPool) {
            fc2()
        }
        ...
    }

    private suspend fun fc2() {
        println("C2 Thread: ${Thread.currentThread()}")
        println("C2 Start")
        delay(5000L)
        println("C2 World! 2")
    }

    private suspend fun fc1() {
        println("C1 Thread: ${Thread.currentThread()}")
        println("C1 Start")
        delay(3000L)
        println("C1 World! 1")
    }
```
可以看出，我们这里的fc1, fc2函数是suspend fun。


## 9.4 协程是轻量级的

直接运行下面的代码：

```
    fun testThread() {
        val jobs = List(100_1000) {
            Thread({
                Thread.sleep(1000L)
                print(".")
            })
        }
        jobs.forEach { it.start() }
        jobs.forEach { it.join() }
    }
```
我们应该会看到输出报错：
```
Exception in thread "main" java.lang.OutOfMemoryError: unable to create new native thread
	at java.lang.Thread.start0(Native Method)
	at java.lang.Thread.start(Thread.java:714)
	at com.easy.kotlin.LightWeightCoroutinesDemo.testThread(LightWeightCoroutinesDemo.kt:30)
	at com.easy.kotlin.LightWeightCoroutinesDemoKt.main(LightWeightCoroutinesDemo.kt:40)
...........................................................................................
```

我们这里直接启动了100，000个线程，并join到一起打印".", 不出意外的我们收到了` java.lang.OutOfMemoryError`。

这个异常问题本质原因是我们创建了太多的线程，而能创建的线程数是有限制的，导致了异常的发生。在Java中， 当我们创建一个线程的时候，虚拟机会在JVM内存创建一个Thread对象同时创建一个操作系统线程，而这个系统线程的内存用的不是JVMMemory，而是系统中剩下的内存(MaxProcessMemory - JVMMemory - ReservedOsMemory)。 能创建的线程数的具体计算公式如下： 

> Number of Threads  = (MaxProcessMemory - JVMMemory - ReservedOsMemory) / (ThreadStackSize) 

其中，参数说明如下：

|                 参数            |                      说明                      |
|------------------------|----------------------------------|
|MaxProcessMemory  |指的是一个进程的最大内存|
|JVMMemory        | JVM内存|
|ReservedOsMemory  |保留的操作系统内存|
|ThreadStackSize     | 线程栈的大小|

我们通常在优化这种问题的时候，要么是采用减小thread stack的大小的方法，要么是采用减小heap或permgen初始分配的大小方法等方式来临时解决问题。

在协程中，情况完全就不一样了。我们看一下实现上面的逻辑的协程代码：

```
    fun testLightWeightCoroutine() = runBlocking {
        val jobs = List(100_000) {
            // create a lot of coroutines and list their jobs
            launch(CommonPool) {
                delay(1000L)
                print(".")
            }
        }
        jobs.forEach { it.join() } // wait for all jobs to complete
    }
```
运行上面的代码，我们将看到输出：
```
START: 21:22:28.913
.....................
.....................(100000个)
.....END: 21:22:30.956
```
上面的程序在2s左右的时间内正确执行完毕。


## 9.5 协程 vs 守护线程

在Java中有两类线程：用户线程 (User Thread)、守护线程 (Daemon Thread)。 

所谓守护 线程，是指在程序运行的时候在后台提供一种通用服务的线程，比如垃圾回收线程就是一个很称职的守护者，并且这种线程并不属于程序中不可或缺的部分。因此，当所有的非守护线程结束时，程序也就终止了，同时会杀死进程中的所有守护线程。

我们来看一段Thread的守护线程的代码：
```
    fun testDaemon2() {
        val t = Thread({
            repeat(100) { i ->
                println("I'm sleeping $i ...")
                Thread.sleep(500L)
            }
        })
        t.isDaemon = true // 必须在启动线程前调用,否则会报错：Exception in thread "main" java.lang.IllegalThreadStateException
        t.start()
        Thread.sleep(2000L) // just quit after delay
    }
```
这段代码启动一个线程，并设置为守护线程。线程内部是间隔500ms 重复打印100次输出。外部主线程睡眠2s。

运行这段代码，将会输出：

```
I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
I'm sleeping 3 ...
```


协程跟守护线程很像，用协程来写上面的逻辑，代码如下：

```
    fun testDaemon1() = runBlocking {
        launch(CommonPool) {
            repeat(100) { i ->
                println("I'm sleeping $i ...")
                delay(500L)
            }
        }
        delay(2000L) // just quit after delay
    }
```

运行这段代码，我们发现也输出：
```
I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
I'm sleeping 3 ...

```

我们可以看出，活动的协程不会使进程保持活动状态。它们的行为就像守护程序线程。



## 9.6 协程执行的取消

我们知道，启动函数launch返回一个Job引用当前协程，该Job引用可用于取消正在运行协程:

```
    fun testCancellation() = runBlocking<Unit> {
        val job = launch(CommonPool) {
            repeat(1000) { i ->
                println("I'm sleeping $i ... CurrentThread: ${Thread.currentThread()}")
                delay(500L)
            }
        }
        delay(1300L)
        println("CurrentThread: ${Thread.currentThread()}")
        println("Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")
        val b1 = job.cancel() // cancels the job
        println("job cancel: $b1")
        delay(1300L)
        println("Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")

        val b2 = job.cancel() // cancels the job, job already canceld, return false
        println("job cancel: $b2")

        println("main: Now I can quit.")
    }
```
运行上面的代码，将会输出：
```
I'm sleeping 0 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 1 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 2 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
CurrentThread: Thread[main,5,main]
Job is alive: true  Job is completed: false
job cancel: true
Job is alive: false  Job is completed: true
job cancel: false
main: Now I can quit.
```

我们可以看出，当job还在运行时，isAlive是true，isCompleted是false。当调用job.cancel取消该协程任务，cancel函数本身返回true,  此时协程的打印动作就停止了。此时，job的状态是isAlive是false，isCompleted是true。 如果，再次调用job.cancel函数，我们将会看到cancel函数返回的是false。



### 9.6.1 计算代码的协程取消失效

kotlinx 协程的所有suspend函数都是可以取消的。我们可以通过job的isActive状态来判断协程的状态，或者检查手否有抛出 CancellationException 时取消。

例如，协程正工作在循环计算中，并且不检查协程当前的状态, 那么调用cancel来取消协程将无法停止协程的运行, 如下面的示例所示:

```
    fun testCooperativeCancellation1() = runBlocking<Unit> {
        val job = launch(CommonPool) {
            var nextPrintTime = 0L
            var i = 0
            while (i < 20) { // computation loop
                val currentTime = System.currentTimeMillis()
                if (currentTime >= nextPrintTime) {
                    println("I'm sleeping ${i++} ... CurrentThread: ${Thread.currentThread()}")
                    nextPrintTime = currentTime + 500L
                }
            }
        }
        delay(3000L)
        println("CurrentThread: ${Thread.currentThread()}")
        println("Before cancel, Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")

        val b1 = job.cancel() // cancels the job
        println("job cancel1: $b1")
        println("After Cancel, Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")

        delay(30000L)

        val b2 = job.cancel() // cancels the job, job already canceld, return false
        println("job cancel2: $b2")

        println("main: Now I can quit.")
    }
```

运行上面的代码，输出：

```
I'm sleeping 0 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 1 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
...
I'm sleeping 6 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
CurrentThread: Thread[main,5,main]
Before cancel, Job is alive: true  Job is completed: false
job cancel1: true
After Cancel, Job is alive: false  Job is completed: true
I'm sleeping 7 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
...
I'm sleeping 18 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 19 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
job cancel2: false
main: Now I can quit.
```
我们可以看出，即使我们调用了cancel函数，当前的job状态isAlive是false了，但是协程的代码依然一直在运行，并没有停止。

### 9.6.2 计算代码协程的有效取消

有两种方法可以使计算代码取消成功。

#### 方法一： 显式检查取消状态isActive

我们直接给出实现的代码：
```
fun testCooperativeCancellation2() = runBlocking<Unit> {
        val job = launch(CommonPool) {
            var nextPrintTime = 0L
            var i = 0
            while (i < 20) { // computation loop

                if (!isActive) {
                    return@launch
                }

                val currentTime = System.currentTimeMillis()
                if (currentTime >= nextPrintTime) {
                    println("I'm sleeping ${i++} ... CurrentThread: ${Thread.currentThread()}")
                    nextPrintTime = currentTime + 500L
                }
            }
        }
        delay(3000L)
        println("CurrentThread: ${Thread.currentThread()}")
        println("Before cancel, Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")
        val b1 = job.cancel() // cancels the job
        println("job cancel1: $b1")
        println("After Cancel, Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")

        delay(3000L)
        val b2 = job.cancel() // cancels the job, job already canceld, return false
        println("job cancel2: $b2")

        println("main: Now I can quit.")
    }
```
运行这段代码，输出：
```
I'm sleeping 0 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 1 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 2 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 3 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 4 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 5 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 6 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
CurrentThread: Thread[main,5,main]
Before cancel, Job is alive: true  Job is completed: false
job cancel1: true
After Cancel, Job is alive: false  Job is completed: true
job cancel2: false
main: Now I can quit.
```

正如您所看到的, 现在这个循环可以被取消了。这里的isActive属性是CoroutineScope中的属性。这个接口的定义是：


```
public interface CoroutineScope {
    public val isActive: Boolean
    public val context: CoroutineContext
}
```

该接口用于通用协程构建器的接收器，以便协程中的代码可以方便的访问其isActive状态值（取消状态），以及其上下文CoroutineContext信息。
 

#### 方法二： 循环调用一个挂起函数yield()

该方法实质上是通过job的isCompleted状态值来捕获CancellationException完成取消功能。


我们只需要在while循环体中循环调用yield()来检查该job的取消状态，如果已经被取消，那么isCompleted值将会是true，yield函数就直接抛出CancellationException异常，从而完成取消的功能：

```
        val job = launch(CommonPool) {
            var nextPrintTime = 0L
            var i = 0
            while (i < 20) { // computation loop

                yield()

                val currentTime = System.currentTimeMillis()
                if (currentTime >= nextPrintTime) {
                    println("I'm sleeping ${i++} ... CurrentThread: ${Thread.currentThread()}")
                    nextPrintTime = currentTime + 500L
                }
            }
        }
```

运行上面的代码，输出：
```
I'm sleeping 0 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-1,5,main]
I'm sleeping 1 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-2,5,main]
I'm sleeping 2 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-2,5,main]
I'm sleeping 3 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-3,5,main]
I'm sleeping 4 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-3,5,main]
I'm sleeping 5 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-3,5,main]
I'm sleeping 6 ... CurrentThread: Thread[ForkJoinPool.commonPool-worker-2,5,main]
CurrentThread: Thread[main,5,main]
Before cancel, Job is alive: true  Job is completed: false
job cancel1: true
After Cancel, Job is alive: false  Job is completed: true
job cancel2: false
main: Now I can quit.
```


如果我们想看看yield函数抛出的异常，我们可以加上try catch打印出日志：
```
try {
    yield()
} catch (e: Exception) {
    println("$i ${e.message}")
}
```

我们可以看到类似：Job was cancelled 这样的信息。


这个yield函数的实现是：
```
suspend fun yield(): Unit = suspendCoroutineOrReturn sc@ { cont ->
    val context = cont.context
    val job = context[Job]
    if (job != null && job.isCompleted) throw job.getCompletionException()
    if (cont !is DispatchedContinuation<Unit>) return@sc Unit
    if (!cont.dispatcher.isDispatchNeeded(context)) return@sc Unit
    cont.dispatchYield(job, Unit)
    COROUTINE_SUSPENDED
}
```

如果调用此挂起函数时，当前协程的Job已经完成 (isActive = false, isCompleted = true)，当前协程将以CancellationException取消。



### 9.6.3 在finally中的协程代码

当我们取消一个协程任务时，如果有`try {...} finally {...}`代码块，那么finally {...}中的代码会被正常执行完毕：
```
    fun finallyCancelDemo() = runBlocking {
        val job = launch(CommonPool) {
            try {
                repeat(1000) { i ->
                    println("I'm sleeping $i ...")
                    delay(500L)
                }
            } finally {
                println("I'm running finally")
            }
        }
        delay(2000L)
        println("Before cancel, Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")
        job.cancel()
        println("After cancel, Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")
        delay(2000L)
        println("main: Now I can quit.")
    }
```

运行这段代码，输出：
```
I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
I'm sleeping 3 ...
Before cancel, Job is alive: true  Job is completed: false
I'm running finally
After cancel, Job is alive: false  Job is completed: true
main: Now I can quit.
```

我们可以看出，在调用cancel之后，就算当前协程任务Job已经结束了，`finally{...}`中的代码依然被正常执行。


但是，如果我们在`finally{...}`中放入挂起函数：
```
    fun finallyCancelDemo() = runBlocking {
        val job = launch(CommonPool) {
            try {
                repeat(1000) { i ->
                    println("I'm sleeping $i ...")
                    delay(500L)
                }
            } finally {
                println("I'm running finally")
                delay(1000L)
                println("And I've delayed for 1 sec ?")
            }
        }
        delay(2000L)
        println("Before cancel, Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")
        job.cancel()
        println("After cancel, Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")
        delay(2000L)
        println("main: Now I can quit.")
    }
```

运行上述代码，我们将会发现只输出了一句：I'm running finally。因为主线程在挂起函数`delay(1000L) `以及后面的打印逻辑还没执行完，就已经结束退出。

```
            } finally {
                println("I'm running finally")
                delay(1000L)
                println("And I've delayed for 1 sec ?")
            }
```


### 9.6.4 协程执行不可取消的代码块

如果我们想要上面的例子中的`finally{...}`完整执行，不被取消函数操作所影响，我们可以使用 run 函数和 NonCancellable 上下文将相应的代码包装在 run (NonCancellable) {...} 中, 如下面的示例所示:

```
    fun testNonCancellable() = runBlocking {
        val job = launch(CommonPool) {
            try {
                repeat(1000) { i ->
                    println("I'm sleeping $i ...")
                    delay(500L)
                }
            } finally {
                run(NonCancellable) {
                    println("I'm running finally")
                    delay(1000L)
                    println("And I've just delayed for 1 sec because I'm non-cancellable")
                }
            }
        }
        delay(2000L)
        println("main: I'm tired of waiting!")
        job.cancel()
        delay(2000L)
        println("main: Now I can quit.")
    }
```

运行输出：
```
I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
I'm sleeping 3 ...
main: I'm tired of waiting!
I'm running finally
And I've just delayed for 1 sec because I'm non-cancellable
main: Now I can quit.
```

## 9.7 设置协程超时时间

我们通常取消协同执行的原因给协程的执行时间设定一个执行时间上限。我们也可以使用 withTimeout 函数来给一个协程任务的执行设定最大执行时间，超出这个时间，就直接终止掉。代码示例如下:

```
    fun testTimeouts() = runBlocking {
        withTimeout(3000L) {
            repeat(100) { i ->
                println("I'm sleeping $i ...")
                delay(500L)
            }
        }
    }
```
运行上述代码，我们将会看到如下输出：
```
I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
I'm sleeping 3 ...
I'm sleeping 4 ...
I'm sleeping 5 ...
Exception in thread "main" kotlinx.coroutines.experimental.TimeoutException: Timed out waiting for 3000 MILLISECONDS
	at kotlinx.coroutines.experimental.TimeoutExceptionCoroutine.run(Scheduled.kt:110)
	at kotlinx.coroutines.experimental.EventLoopImpl$DelayedRunnableTask.invoke(EventLoop.kt:199)
	at kotlinx.coroutines.experimental.EventLoopImpl$DelayedRunnableTask.invoke(EventLoop.kt:195)
	at kotlinx.coroutines.experimental.EventLoopImpl.processNextEvent(EventLoop.kt:111)
	at kotlinx.coroutines.experimental.BlockingCoroutine.joinBlocking(Builders.kt:205)
	at kotlinx.coroutines.experimental.BuildersKt.runBlocking(Builders.kt:150)
	at kotlinx.coroutines.experimental.BuildersKt.runBlocking$default(Builders.kt:142)
	at com.easy.kotlin.CancellingCoroutineDemo.testTimeouts(CancellingCoroutineDemo.kt:169)
	at com.easy.kotlin.CancellingCoroutineDemoKt.main(CancellingCoroutineDemo.kt:193)

```


由 withTimeout 抛出的 TimeoutException 是 CancellationException 的一个子类。这个TimeoutException类型定义如下：
```
private class TimeoutException(
    time: Long,
    unit: TimeUnit,
    @JvmField val coroutine: Job
) : CancellationException("Timed out waiting for $time $unit")
```

如果您需要在超时时执行一些附加操作, 则可以把逻辑放在 try {...} catch (e: CancellationException) {...} 代码块中。例如：
```
    try {
        ccd.testTimeouts()
    } catch (e: CancellationException) {
        println("I am timed out!")
    }
```

## 9.8 挂起函数的组合执行

本节我们介绍挂起函数组合的各种方法。

### 9.8.1 按默认顺序执行

假设我们有两个在别处定义的挂起函数：

```
    suspend fun doJob1(): Int {
        println("Doing Job1 ...")
        delay(1000L) // 此处模拟我们的工作代码
        println("Job1 Done")
        return 10
    }

    suspend fun doJob2(): Int {
        println("Doing Job2 ...")
        delay(1000L) // 此处模拟我们的工作代码
        println("Job2 Done")
        return 20
    }

```


如果需要依次调用它们, 我们只需要使用正常的顺序调用, 因为协同中的代码 (就像在常规代码中一样) 是默认的顺序执行。下面的示例通过测量执行两个挂起函数所需的总时间来演示:

```
    fun testSequential() = runBlocking<Unit> {
        val time = measureTimeMillis {
            val one = doJob1()
            val two = doJob2()
            println("[testSequential] 最终结果： ${one + two}")
        }
        println("[testSequential] Completed in $time ms")
    }

```

执行上面的代码，我们将得到输出：

```
Doing Job1 ...
Job1 Done
Doing Job2 ...
Job2 Done
[testSequential] 最终结果： 30
[testSequential] Completed in 6023 ms
```
可以看出，我们的代码是跟普通的代码一样顺序执行下去。

### 9.8.2 使用async异步并发执行

上面的例子中，如果在调用 doJob1 和 doJob2 之间没有时序上的依赖关系, 并且我们希望通过同时并发地执行这两个函数来更快地得到答案,  那该怎么办呢？这个时候，我们就可以使用async来实现异步。代码示例如下：

```
    fun testAsync() = runBlocking<Unit> {
        val time = measureTimeMillis {
            val one = async(CommonPool) { doJob1() }
            val two = async(CommonPool) { doJob2() }
            println("最终结果： ${one.await() + two.await()}")
        }
        println("Completed in $time ms")
    }
```
如果跟上面同步的代码一起执行对比，我们可以看到如下输出：

```
Doing Job1 ...
Job1 Done
Doing Job2 ...
Job2 Done
[testSequential] 最终结果： 30
[testSequential] Completed in 6023 ms
Doing Job1 ...
Doing Job2 ...
Job1 Done
Job2 Done
[testAsync] 最终结果： 30
[testAsync] Completed in 3032 ms
```

我们可以看出，使用async函数，我们的两个Job并发的执行了，并发花的时间要比顺序的执行的要快将近两倍。因为，我们有两个任务在并发的执行。

从概念上讲, async跟launch类似, 它启动一个协程, 它与其他协程并发地执行。

不同之处在于, launch返回一个任务Job对象, 不带任何结果值；而async返回一个延迟任务对象Deferred，一种轻量级的非阻塞性future,  它表示后面会提供结果。

在上面的示例代码中，我们使用Deferred调用 await() 函数来获得其最终结果。另外，延迟任务Deferred也是Job类型, 它继承自Job，所以它也有isActive、isCompleted属性，也有join()、cancel()函数，因此我们也可以在需要时取消它。Deferred接口定义如下：

```
public interface Deferred<out T> : Job {
    val isCompletedExceptionally: Boolean
    val isCancelled: Boolean
    public suspend fun await(): T
    public fun <R> registerSelectAwait(select: SelectInstance<R>, block: suspend (T) -> R)
    public fun getCompleted(): T
    @Deprecated(message = "Use `isActive`", replaceWith = ReplaceWith("isActive"))
    public val isComputing: Boolean get() = isActive
}
```

其中，常用的属性和函数说明如下：

|                   名称                          |                      说明                  |
|----------------------------------|-------------------------------|
|isCompletedExceptionally| 当协程在计算过程中有异常failed 或被取消，返回true。 这也意味着`isActive`等于 `false` ，同时 `isCompleted`等于 `true` |
|isCancelled |如果当前延迟任务被取消，返回true|
|suspend fun await() |等待此延迟任务完成，而不阻塞线程；如果延迟任务完成, 则返回结果值或引发相应的异常。|


延迟任务对象Deferred的状态与对应的属性值如下表所示：

 | 状态  | isActive |isCompleted | isCompletedExceptionally | isCancelled |
| -------------------------------- | ---------- | ------------- | -------------------------- | ------------- |
| _New_ (可选初始状态)   | `false`    | `false`       | `false`                    | `false`       |
| _Active_ (默认初始状态) | `true`     | `false`       | `false`                    | `false`       |
| _Resolved_  (最终状态)        | `false`    | `true`        | `false`                    | `false`       |
| _Failed_    (最终状态)        | `false`    | `true`        | `true`                     | `false`       |
| _Cancelled_ (最终状态)        | `false`    | `true`        | `true`                     | `true`        |


## 9.9 协程上下文与调度器

到这里，我们已经看到了下面这些启动协程的方式：
```
launch(CommonPool) {...}
async(CommonPool) {...}
run(NonCancellable) {...}
```

这里的CommonPool 和 NonCancellable 是协程上下文（coroutine contexts）。本小节我们简单介绍一下自定义协程上下文。

### 9.9.1 调度和线程

协程上下文包括一个协程调度程序, 它可以指定由哪个线程来执行协程。调度器可以将协程的执行调度到一个线程池，限制在特定的线程中；也可以不作任何限制，让它无约束地运行。请看下面的示例:

```
    fun testDispatchersAndThreads() = runBlocking {
        val jobs = arrayListOf<Job>()
        jobs += launch(Unconfined) {
            // 未作限制 -- 将会在 main thread 中执行
            println("Unconfined: I'm working in thread ${Thread.currentThread()}")
        }
        jobs += launch(context) {
            // 父协程的上下文 ： runBlocking coroutine
            println("context: I'm working in thread ${Thread.currentThread()}")
        }
        jobs += launch(CommonPool) {
            // 调度指派给 ForkJoinPool.commonPool
            println("CommonPool: I'm working in thread ${Thread.currentThread()}")
        }
        jobs += launch(newSingleThreadContext("MyOwnThread")) {
            // 将会在这个协程自己的新线程中执行
            println("newSingleThreadContext: I'm working in thread ${Thread.currentThread()}")
        }
        jobs.forEach { it.join() }
    }
```

运行上面的代码，我们将得到以下输出 (可能按不同的顺序):
```
Unconfined: I'm working in thread Thread[main,5,main]
CommonPool: I'm working in thread Thread[ForkJoinPool.commonPool-worker-1,5,main]
newSingleThreadContext: I'm working in thread Thread[MyOwnThread,5,main]
context: I'm working in thread Thread[main,5,main]
```

从上面的结果，我们可以看出：
使用无限制的Unconfined上下文的协程运行在主线程中；
继承了 runBlocking {...} 的context的协程继续在主线程中执行；
而CommonPool在ForkJoinPool.commonPool中；
我们使用newSingleThreadContext函数新建的协程上下文，该协程运行在自己的新线程Thread[MyOwnThread,5,main]中。


另外，我们还可以在使用 runBlocking的时候显式指定上下文, 同时使用 run 函数来更改协程的上下文：

```
    fun log(msg: String) = println("${Thread.currentThread()} $msg")

    fun testRunBlockingWithSpecifiedContext() = runBlocking {
        log("$context")
        log("${context[Job]}")
        log("开始")

        val ctx1 = newSingleThreadContext("线程A")
        val ctx2 = newSingleThreadContext("线程B")
        runBlocking(ctx1) {
            log("Started in Context1")
            run(ctx2) {
                log("Working in Context2")
            }
            log("Back to Context1")
        }
        log("结束")
    }
```

运行输出：

```
Thread[main,5,main] [BlockingCoroutine{Active}@b1bc7ed, EventLoopImpl@7cd84586]
Thread[main,5,main] BlockingCoroutine{Active}@b1bc7ed
Thread[main,5,main] 开始
Thread[线程A,5,main] Started in Context1
Thread[线程B,5,main] Working in Context2
Thread[线程A,5,main] Back to Context1
Thread[main,5,main] 结束
```

### 9.9.2 父子协程

当我们使用协程A的上下文启动另一个协程B时,  B将成为A的子协程。当父协程A任务被取消时, B以及它的所有子协程都会被递归地取消。代码示例如下：

```
    fun testChildrenCoroutine()= runBlocking<Unit> {
        val request = launch(CommonPool) {
            log("ContextA1: ${context}")

            val job1 = launch(CommonPool) {
                println("job1: 独立的协程上下文!")
                delay(1000)
                println("job1: 不会受到request.cancel()的影响")
            }
            // 继承父上下文：request的context
            val job2 = launch(context) {
                log("ContextA2: ${context}")
                println("job2: 是request coroutine的子协程")
                delay(1000)
                println("job2: 当request.cancel()，job2也会被取消")
            }
            job1.join()
            job2.join()
        }
        delay(500)
        request.cancel()
        delay(1000)
        println("main: Who has survived request cancellation?")
    }
```

运行输出：

```
Thread[ForkJoinPool.commonPool-worker-1,5,main] ContextA1: [StandaloneCoroutine{Active}@5b646af2, CommonPool]
job1: 独立的协程上下文!
Thread[ForkJoinPool.commonPool-worker-3,5,main] ContextA2: [StandaloneCoroutine{Active}@75152aa4, CommonPool]
job2: 是request coroutine的子协程
job1: 不会受到request.cancel()的影响
main: Who has survived request cancellation?
```

## 9.10 通道

延迟对象提供了一种在协程之间传输单个值的方法。而通道（Channel）提供了一种传输数据流的方法。通道是使用 SendChannel 和使用 ReceiveChannel 之间的非阻塞通信。

### 9.10.1 通道 vs 阻塞队列

通道的概念类似于 阻塞队列（BlockingQueue）。在Java的Concurrent包中，BlockingQueue很好的解决了多线程中如何高效安全“传输”数据的问题。它有两个常用的方法如下：

- E take():  取走BlockingQueue里排在首位的对象,若BlockingQueue为空, 阻塞进入等待状态直到BlockingQueue有新的数据被加入; 

- put(E e): 把对象 e 加到BlockingQueue里, 如果BlockQueue没有空间,则调用此方法的线程被阻塞，直到BlockingQueue里面有空间再继续。

通道跟阻塞队列一个关键的区别是：通道有挂起的操作, 而不是阻塞的, 同时它可以关闭。


代码示例：

```
package com.easy.kotlin

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

/**
 * Created by jack on 2017/7/13.
 */
class ChannelsDemo {
    fun testChannel() = runBlocking<Unit> {
        val channel = Channel<Int>()
        launch(CommonPool) {
            for (x in 1..10) channel.send(x * x)
        }
        println("channel = ${channel}")
        // here we print five received integers:
        repeat(10) { println(channel.receive()) }
        println("Done!")
    }
}


fun main(args: Array<String>) {
    val cd = ChannelsDemo()
    cd.testChannel()
}
```

运行输出：
```
channel = kotlinx.coroutines.experimental.channels.RendezvousChannel@2e817b38
1
4
9
16
25
36
49
64
81
100
Done!
```

我们可以看出使用`Channel<Int>()`背后调用的是会合通道`RendezvousChannel()`，会合通道中没有任何缓冲区。send函数被挂起直到另外一个协程调用receive函数， 然后receive函数挂起直到另外一个协程调用send函数。它是一个完全无锁的实现。



###  9.10.2 关闭通道和迭代遍历元素

与队列不同, 通道可以关闭, 以指示没有更多的元素。在接收端, 可以使用 for 循环从通道接收元素。代码示例：

```
    fun testClosingAndIterationChannels() = runBlocking {
        val channel = Channel<Int>()
        launch(CommonPool) {
            for (x in 1..5) channel.send(x * x)
            channel.close() // 我们结束 sending
        }
        // 打印通道中的值，直到通道关闭
        for (x in channel) println(x)
        println("Done!")
    }
```


其中， close函数在这个通道上发送一个特殊的 "关闭令牌"。这是一个幂等运算：对此函数的重复调用不起作用, 并返回 "false"。此函数执行后，`isClosedForSend`返回 "true"。但是, `ReceiveChannel`的`isClosedForReceive `在所有之前发送的元素收到之后才返回 "true"。

我们把上面的代码加入打印日志：
```
    fun testClosingAndIterationChannels() = runBlocking {
        val channel = Channel<Int>()
        launch(CommonPool) {
            for (x in 1..5) {
                channel.send(x * x)
            }
            println("Before Close => isClosedForSend = ${channel.isClosedForSend}")
            channel.close() // 我们结束 sending
            println("After Close => isClosedForSend = ${channel.isClosedForSend}")
        }
        // 打印通道中的值，直到通道关闭
        for (x in channel) {
            println("${x} => isClosedForReceive = ${channel.isClosedForReceive}")
        }
        println("Done!  => isClosedForReceive = ${channel.isClosedForReceive}")
    }
```

运行输出：

```
1 => isClosedForReceive = false
4 => isClosedForReceive = false
9 => isClosedForReceive = false
16 => isClosedForReceive = false
25 => isClosedForReceive = false
Before Close => isClosedForSend = false
After Close => isClosedForSend = true
Done!  => isClosedForReceive = true
```


### 9.10.3 生产者-消费者模式

使用协程生成元素序列的模式非常常见。这是在并发代码中经常有的生产者-消费者模式。代码示例：

```
    fun produceSquares() = produce<Int>(CommonPool) {
        for (x in 1..7) send(x * x)
    }

    fun consumeSquares() = runBlocking{
        val squares = produceSquares()
        squares.consumeEach { println(it) }
        println("Done!")
    }
```

这里的produce函数定义如下：
```
public fun <E> produce(
    context: CoroutineContext,
    capacity: Int = 0,
    block: suspend ProducerScope<E>.() -> Unit
): ProducerJob<E> {
    val channel = Channel<E>(capacity)
    return ProducerCoroutine(newCoroutineContext(context), channel).apply {
        initParentJob(context[Job])
        block.startCoroutine(this, this)
    }
}
```

其中，参数说明如下：

| 参数名      |   说明             |
|-------------|---------------|
|context| 协程上下文 |
|capacity | 通道缓存容量大小 (默认没有缓存)|
|block | 协程代码块|

produce函数会启动一个新的协程,  协程中发送数据到通道来生成数据流，并以 ProducerJob对象返回对协程的引用。ProducerJob继承了Job, ReceiveChannel类型。



## 9.11 管道

### 9.11.1 生产无限序列

管道（Pipeline）是一种模式,  我们可以用一个协程生产无限序列:

```
    fun produceNumbers() = produce<Long>(CommonPool) {
        var x = 1L
        while (true) send(x++) // infinite stream of integers starting from 1
    }
```

我们的消费序列的函数如下：
```
    fun produceNumbers() = produce<Long>(CommonPool) {
        var x = 1L
        while (true) send(x++) // infinite stream of integers starting from 1
    }
```

主代码启动并连接整个管线:

```

    fun testPipeline() = runBlocking {
        val numbers = produceNumbers() // produces integers from 1 and on
        val squares = consumeNumbers(numbers) // squares integers
        //for (i in 1..6) println(squares.receive())
        while (true) {
            println(squares.receive())
        }
        println("Done!")
        squares.cancel()
        numbers.cancel()
    }
```

运行上面的代码，我们将会发现控制台在打印一个无限序列，完全没有停止的意思。


### 9.11.2 管道与无穷质数序列

我们使用协程管道来生成一个无穷质数序列。

我们从无穷大的自然数序列开始：

```
    fun numbersProducer(context: CoroutineContext, start: Int) = produce<Int>(context) {
        var n = start
        while (true) send(n++) // infinite stream of integers from start
    }
```

这次我们引入一个显式上下文参数context,  以便调用方可以控制我们的协程运行的位置。

下面的管道将筛选传入的数字流,  过滤掉可以被当前质数整除的所有数字： 

```
    fun filterPrimes(context: CoroutineContext, numbers: ReceiveChannel<Int>, prime: Int) = produce<Int>(context) {
        for (x in numbers) if (x % prime != 0) send(x)
    }
```


现在我们通过从2开始, 从当前通道中取一个质数, 并为找到的每个质数启动新的管道阶段, 从而构建出我们的管道:

```
numbersFrom(2) -> filterPrimes(2) -> filterPrimes(3) -> filterPrimes(5) -> filterPrimes(7) ... 
```

测试无穷质数序列：

```
    fun producePrimesSequences() = runBlocking {
        var producerJob = numbersProducer(context, 2)

        while (true) {
            val prime = producerJob.receive()
            print("${prime} \t")
            producerJob = filterPrimes(context, producerJob, prime)
        }
    }
```


运行上面的代码，我们将会看到控制台一直在无限打印出质数序列：


![螢幕快照 2017-07-14 01.41.41.png](http://upload-images.jianshu.io/upload_images/1233356-86fc0b9dc0126229.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


### 9.11.3 通道缓冲区

我们可以给通道设置一个缓冲区：

```
fun main(args: Array<String>) = runBlocking<Unit> {
    val channel = Channel<Int>(4) // 创建一个缓冲区容量为4的通道
    launch(context) {
        repeat(10) {
            println("Sending $it")
            channel.send(it) // 当缓冲区已满的时候， send将会挂起
        }
    }
    delay(1000)
}
```
输出：

```
Sending 0
Sending 1
Sending 2
Sending 3
Sending 4
```


## 9.12 构建无穷惰性序列

我们可以使用 buildSequence 序列生成器 ，构建一个无穷惰性序列。

```
    val fibonacci = buildSequence {
        yield(1L)
        var current = 1L
        var next = 1L
        while (true) {
            yield(next)
            val tmp = current + next
            current = next
            next = tmp
        }
    }

```

我们通过buildSequence创建一个协程，生成一个惰性的无穷斐波那契数列。该协程通过调用 yield() 函数来产生连续的斐波纳契数。

我们可以从该序列中取出任何有限的数字列表，例如 

```
println(fibonacci.take(16).toList())
```
的结果是：
```
[1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987]
```

## 9.13 协程与线程比较

直接先说区别，协程是编译器级的，而线程是操作系统级的。

协程通常是由编译器来实现的机制。线程看起来也在语言层次，但是内在原理却是操作系统先有这个东西，然后通过一定的API暴露给用户使用，两者在这里有不同。

协程就是用户空间下的线程。用协程来做的东西，用线程或进程通常也是一样可以做的，但往往多了许多加锁和通信的操作。

线程是抢占式，而协程是非抢占式的，所以需要用户自己释放使用权来切换到其他协程，因此同一时间其实只有一个协程拥有运行权，相当于单线程的能力。

协程并不是取代线程, 而且抽象于线程之上, 线程是被分割的CPU资源, 协程是组织好的代码流程, 协程需要线程来承载运行, 线程是协程的资源, 但协程不会直接使用线程, 协程直接利用的是执行器(Interceptor), 执行器可以关联任意线程或线程池, 可以使当前线程, UI线程, 或新建新程.。

线程是协程的资源。协程通过Interceptor来间接使用线程这个资源。

## 9.14 协程的好处

与多线程、多进程等并发模型不同，协程依靠user-space调度，而线程、进程则是依靠kernel来进行调度。线程、进程间切换都需要从用户态进入内核态，而协程的切换完全是在用户态完成，且不像线程进行抢占式调度，协程是非抢占式的调度。

通常多个运行在同一调度器中的协程运行在一个线程内，这也消除掉了多线程同步等带来的编程复杂性。同一时刻同一调度器中的协程只有一个会处于运行状态。

我们使用协程，程序只在用户空间内切换上下文，不再陷入内核来做线程切换，这样可以避免大量的用户空间和内核空间之间的数据拷贝，降低了CPU的消耗，从而大大减缓高并发场景时CPU瓶颈的窘境。

另外，使用协程，我们不再需要像异步编程时写那么一堆callback函数，代码结构不再支离破碎，整个代码逻辑上看上去和同步代码没什么区别，简单，易理解，优雅。

我们使用协程，我们可以很简单地实现一个可以随时中断随时恢复的函数。

一些 API 启动长时间运行的操作(例如网络 IO、文件 IO、CPU 或 GPU 密集型任务等)，并要求调用者阻塞直到它们完成。协程提供了一种避免阻塞线程并用更廉价、更可控的操作替代线程阻塞的方法：协程挂起。


协程通过将复杂性放入库来简化异步编程。程序的逻辑可以在协程中顺序地表达，而底层库会为我们解决其异步性。该库可以将用户代码的相关部分包装为回调、订阅相关事件、在不同线程(甚至不同机器)上调度执行，而代码则保持如同顺序执行一样简单。

### 9.14.1 阻塞 vs 挂起

协程可以被挂起而无需阻塞线程。而线程阻塞的代价通常是昂贵的，尤其在高负载时，阻塞其中一个会导致一些重要的任务被延迟。

另外，协程挂起几乎是无代价的。不需要上下文切换或者 OS 的任何其他干预。

最重要的是，挂起可以在很大程度上由用户来控制，我们可以决定挂起时做些，并根据需求优化、记日志、拦截处理等。


## 9.15 协程的内部机制

### 9.15.1 基本原理

协程完全通过编译技术实现(不需要来自 VM 或 OS 端的支持)，挂起机制是通过状态机来实现，其中的状态对应于挂起调用。

在挂起时，对应的协程状态与局部变量等一起被存储在编译器生成的类的字段中。在恢复该协程时，恢复局部变量并且状态机从挂起点接着后面的状态往后执行。

挂起的协程，是作为Continuation对象来存储和传递，Continuation中持有协程挂起状态与局部变量。

关于协程工作原理的更多细节可以在这个设计文档中找到：https://github.com/Kotlin/kotlin-coroutines/blob/master/kotlin-coroutines-informal.md。

### 9.15.2 标准 API

协程有三个主要组成部分：

- 语言支持(即如上所述的挂起功能)，
- Kotlin 标准库中的底层核心 API，
- 可以直接在用户代码中使用的高级 API。
- 底层 API：kotlin.coroutines

底层 API 相对较小，并且除了创建更高级的库之外，不应该使用它。 它由两个主要包组成：

kotlin.coroutines.experimental 带有主要类型与下述原语：

createCoroutine()
startCoroutine()
suspendCoroutine()

kotlin.coroutines.experimental.intrinsics 带有甚至更底层的内在函数如 ：

suspendCoroutineOrReturn


大多数基于协程的应用程序级API都作为单独的库发布：kotlinx.coroutines。这个库主要包括下面几大模块：

- 使用 kotlinx-coroutines-core 的平台无关异步编程
- 基于 JDK 8 中的 CompletableFuture 的 API：kotlinx-coroutines-jdk8
- 基于 JDK 7 及更高版本 API 的非阻塞 IO(NIO)：kotlinx-coroutines-nio
- 支持 Swing (kotlinx-coroutines-swing) 和 JavaFx (kotlinx-coroutines-javafx)
- 支持 RxJava：kotlinx-coroutines-rx

这些库既作为使通用任务易用的便利的 API，也作为如何构建基于协程的库的端到端示例。关于这些 API 用法的更多细节可以参考相关文档。



## 本章小结

本章我通过大量实例学习了协程的用法；同时了解了作为轻量级线程的协程是怎样简化的我们的多线程并发编程的。我们看到协程通过挂起机制实现非阻塞的特性大大提升了我们并发性能。

最后，我们还简单介绍了协程的实现的原理以及标准API库。Kotlin的协程的实现大量地调用了Java中的多线程API。所以在Kotlin中，我们仍然完全可以使用Java中的多线程编程。

下一章我们来一起学习Kotlin与Java代码之间的互相调用。

本章示例代码工程：

https://github.com/EasyKotlin/chapter9_coroutines

