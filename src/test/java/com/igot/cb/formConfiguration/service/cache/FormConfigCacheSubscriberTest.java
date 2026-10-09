package com.igot.cb.formConfiguration.service.cache;

import com.igot.cb.util.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormConfigCacheSubscriberTest {

    @Mock
    private JedisPool jedisPool;

    @Mock
    private FormConfigCache formConfigCache;

    private FormConfigCacheSubscriber subscriber;

    @AfterEach
    void tearDown() throws Exception {
        if (subscriber != null) {
            subscriber.stop();
            Thread listenerThread = listenerThreadOf(subscriber);
            if (listenerThread != null) {
                listenerThread.join(3000);
            }
        }
    }

    private Thread listenerThreadOf(FormConfigCacheSubscriber target) throws Exception {
        Field field = FormConfigCacheSubscriber.class.getDeclaredField("listenerThread");
        field.setAccessible(true);
        return (Thread) field.get(target);
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<JedisPubSub> subscriberRefOf(FormConfigCacheSubscriber target) throws Exception {
        Field field = FormConfigCacheSubscriber.class.getDeclaredField("subscriber");
        field.setAccessible(true);
        return (AtomicReference<JedisPubSub>) field.get(target);
    }

    @Test
    void stop_beforeStart_shouldNotThrowAndSkipJedisInteractions() {
        subscriber = new FormConfigCacheSubscriber(jedisPool, formConfigCache);

        subscriber.stop();

        verifyNoInteractions(jedisPool);
    }

    @Test
    void start_shouldSpawnDaemonListenerThread() throws Exception {
        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis unavailable"));
        subscriber = new FormConfigCacheSubscriber(jedisPool, formConfigCache);

        subscriber.start();

        Thread listenerThread = listenerThreadOf(subscriber);
        assertNotNull(listenerThread);
        assertTrue(listenerThread.isDaemon());
        assertTrue(listenerThread.isAlive());
    }

    @Test
    void start_thenStop_shouldTerminateListenerThreadWithinTimeout() throws Exception {
        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis unavailable"));
        subscriber = new FormConfigCacheSubscriber(jedisPool, formConfigCache);

        subscriber.start();
        Thread listenerThread = listenerThreadOf(subscriber);
        subscriber.stop();
        listenerThread.join(3000);

        assertFalse(listenerThread.isAlive());
    }

    @Test
    void listen_subscribesOnFreshConnection_thenExitsWhenStopped() throws Exception {
        Jedis jedis = mock(Jedis.class);
        when(jedisPool.getResource()).thenReturn(jedis);
        doAnswer(invocation -> {
            // Simulate stop() being invoked while subscribe() is blocking, so the
            // listener loop exits immediately once subscribe() returns.
            Field runningField = FormConfigCacheSubscriber.class.getDeclaredField("running");
            runningField.setAccessible(true);
            runningField.set(subscriber, false);
            return null;
        }).when(jedis).subscribe(any(JedisPubSub.class), eq(Constants.FORM_CONFIG_INVALIDATE_CHANNEL));

        subscriber = new FormConfigCacheSubscriber(jedisPool, formConfigCache);
        subscriber.start();
        Thread listenerThread = listenerThreadOf(subscriber);
        listenerThread.join(3000);

        assertFalse(listenerThread.isAlive());
        verify(jedis, times(1)).subscribe(any(JedisPubSub.class), eq(Constants.FORM_CONFIG_INVALIDATE_CHANNEL));
    }

    @Test
    void stop_whileSubscribed_unsubscribesListener() throws Exception {
        subscriber = new FormConfigCacheSubscriber(jedisPool, formConfigCache);
        JedisPubSub pubSub = mock(JedisPubSub.class);
        when(pubSub.isSubscribed()).thenReturn(true);
        subscriberRefOf(subscriber).set(pubSub);

        subscriber.stop();

        verify(pubSub, times(1)).unsubscribe();
    }
}
