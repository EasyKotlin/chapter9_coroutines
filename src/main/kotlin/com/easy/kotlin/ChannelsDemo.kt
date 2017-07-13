package com.easy.kotlin

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.buildSequence

/**
 * Created by jack on 2017/7/13.
 */
class ChannelsDemo {
    fun testChannel() = runBlocking<Unit> {
        val channel = Channel<Int>()
        val job = launch(CommonPool) {
            for (x in 1..5) {
                channel.send(x * x)
            }
        }
        println("channel = ${channel}")
        // here we print five received integers:
        repeat(5) {
            println(channel.receive())
        }

        println("Done!")

    }

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

    fun produceSquares() = produce<Int>(CommonPool) {
        for (x in 1..7) send(x * x)
    }

    fun consumeSquares() = runBlocking {
        val squares = produceSquares()
        squares.consumeEach { println(it) }
        println("Done!")
    }


    fun produceNumbers() = produce<Long>(CommonPool) {
        var x = 1L
        while (true) send(x++) // infinite stream of integers starting from 1
    }


    fun consumeNumbers(numbers: ReceiveChannel<Long>) = produce<Long>(CommonPool) {
        for (x in numbers) send(x * x)
    }


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


    fun numbersProducer(context: CoroutineContext, start: Int) = produce<Int>(context) {
        var n = start
        while (true) send(n++) // infinite stream of integers from start
    }

    fun filterPrimes(context: CoroutineContext, receiveChannel: ReceiveChannel<Int>, prime: Int) = produce<Int>(context) {
        for (x in receiveChannel) if (x % prime != 0) send(x)
    }

    fun producePrimesSequences() = runBlocking {
        var producerJob = numbersProducer(context, 2)

        while (true) {
            val prime = producerJob.receive()
            print("${prime} \t")
            producerJob = filterPrimes(context, producerJob, prime)
        }
    }
}


fun main(args: Array<String>) = runBlocking {
    val cd = ChannelsDemo()
//    cd.testChannel()
//    cd.testClosingAndIterationChannels()
//    cd.consumeSquares()
//    cd.testPipeline()
//    cd.producePrimesSequences()


    val channel = Channel<Int>(4) // 创建一个缓冲区容量为4的通道
    launch(context) {
        repeat(10) {
            println("Sending $it")
            channel.send(it) // 当缓冲区已满的时候， send将会挂起
        }
    }
    delay(1000)


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

    println(fibonacci.take(16).forEach { print("${it} \t") })
    println(fibonacci.take(16).toList())


}
