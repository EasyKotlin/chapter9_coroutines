package com.easy.kotlin

import kotlinx.coroutines.experimental.*

/**
 * Created by jack on 2017/7/12.
 */
class CancellingCoroutineDemo {
    fun testCancellation() = runBlocking<Unit> {
        val job = launch(CommonPool) {
            repeat(1000) { i ->
                println("I'm sleeping $i ... CurrentThread: ${Thread.currentThread()}")
                delay(500L)
            }
        }
        delay(2000L)
        println("CurrentThread: ${Thread.currentThread()}")
        println("Before cancel, Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")
        val b1 = job.cancel() // cancels the job
        println("job cancel: $b1")
        println("After cancel, Job is alive: ${job.isActive}  Job is completed: ${job.isCompleted}")

        delay(2000L)

        val b2 = job.cancel() // cancels the job, job already canceld, return false
        println("job cancel: $b2")

        println("main: Now I can quit.")
    }

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

    fun testCooperativeCancellation3() = runBlocking<Unit> {
        val job = launch(CommonPool) {
            var nextPrintTime = 0L
            var i = 0
            while (i < 20) { // computation loop

                try {
                    yield()
                } catch (e: Exception) {
                    println("$i ${e.message}")
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


    fun testTimeouts() = runBlocking {
        withTimeout(3000L) {
            repeat(100) { i ->
                println("I'm sleeping $i ...")
                delay(500L)
            }
        }
    }
}

fun main(args: Array<String>) {
    val ccd = CancellingCoroutineDemo()
//    ccd.testCancellation()
//    ccd.testCooperativeCancellation1()
//    ccd.testCooperativeCancellation2()
//    ccd.testCooperativeCancellation3()
//    ccd.finallyCancelDemo()
//    ccd.testNonCancellable()
    try {
        ccd.testTimeouts()
    } catch (e: CancellationException) {
        println("I am timed out!")
    }
    //ccd.testTimeouts()


}
