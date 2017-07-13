package com.easy.kotlin

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import java.util.*


/**
 * Created by jack on 2017/7/12.
 */

fun main(args: Array<String>)  = runBlocking<Unit>{
    // 主协程
    println("${format(Date())}: T0")
    // 启动主协程
    //在common thread pool中创建协程
    launch(CommonPool) {
        println("${format(Date())}: T1")
        delay(3000L)
        println("${format(Date())}: T2 Hello,")
    }
    println("${format(Date())}: T3 World!") //  当子协程被delay，主协程仍然继续运行
    delay(5000L)
    println("${format(Date())}: T4")

}

