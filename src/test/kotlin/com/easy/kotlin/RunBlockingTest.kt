package com.easy.kotlin

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Created by jack on 2017/7/12.
 */

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
