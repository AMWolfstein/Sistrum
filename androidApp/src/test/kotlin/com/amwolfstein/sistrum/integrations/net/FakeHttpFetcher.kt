package com.amwolfstein.sistrum.integrations.net

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Scripted provider-test transport. No sockets; requests are recorded before each scripted reply. */
class FakeHttpFetcher {
    data class Request(val url: String, val headers: Map<String, String>, val maxBytes: Int)

    val requests = mutableListOf<Request>()
    private val replies = ArrayDeque<suspend (Request) -> Any>()

    fun enqueue(result: Any) { replies.addLast { result } }
    fun enqueueReply(reply: suspend (Request) -> Any) { replies.addLast(reply) }

    suspend fun get(url: String, headers: Map<String, String>, maxBytes: Int): Any {
        val request = Request(url, headers.toMap(), maxBytes)
        requests.add(request)
        check(replies.isNotEmpty()) { "Unscripted HTTP request: configure FakeHttpFetcher first" }
        return replies.removeFirst()(request)
    }

    /** T022's interface is absent in T021. Type inference lets later tests use `val f: HttpFetcher = fake.asFetcher()`. */
    @Suppress("UNCHECKED_CAST")
    fun <T> asFetcher(): T {
        val api = fetcherClass("HttpFetcher")
        return Proxy.newProxyInstance(api.classLoader, arrayOf(api)) { proxy, method, args ->
            when (method.name) {
                "toString" -> "FakeHttpFetcher"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args!![0]
                "get" -> {
                    val continuation = args!![3] as Continuation<Any>
                    val reply: suspend () -> Any = { get(args[0] as String, args[1] as Map<String, String>, args[2] as Int) }
                    reply.startCoroutineUninterceptedOrReturn(continuation)
                }
                else -> error("Unexpected HttpFetcher method: ${method.name}")
            }
        } as T
    }
}

// Reflection is only a tests-first bridge: no production stubs or duplicate FetchResult hierarchy.
internal fun fetcherClass(name: String): Class<*> = try {
    Class.forName("com.amwolfstein.sistrum.integrations.net.$name")
} catch (_: ClassNotFoundException) {
    throw AssertionError("T022 has not implemented $name (T021 contract)")
}

internal suspend fun fetch(fetcher: Any, url: String, headers: Map<String, String> = emptyMap(), maxBytes: Int = 16): Any =
    suspendCoroutineUninterceptedOrReturn { continuation ->
        val method = fetcherClass("HttpFetcher").getMethod(
            "get", String::class.java, Map::class.java, Int::class.javaPrimitiveType, Continuation::class.java,
        )
        try {
            val result = method.invoke(fetcher, url, headers, maxBytes, continuation)
            if (result === COROUTINE_SUSPENDED) COROUTINE_SUSPENDED else result!!
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }
