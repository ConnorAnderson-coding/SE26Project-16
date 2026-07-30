package com.example.demo.support;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.aspectj.AnnotationAsyncExecutionAspect;

/**
 * 测试用配置：确保 AspectJ LTW (aspectjweaver javaagent) 织入的
 * {@link AnnotationAsyncExecutionAspect} 能正确获取 {@link BeanFactory}，
 * 从而解析 {@code @Async("llmExecutor")} 限定的线程池。
 *
 * <p>生产环境中 {@code @EnableAsync} 默认使用 PROXY 模式，不会主动注册
 * AnnotationAsyncExecutionAspect；但 maven-surefire-plugin 的 aspectjweaver
 * 会在测试时启用 LTW，导致 Aspect 拦截 @Async 方法但找不到 BeanFactory。</p>
 */
@Configuration
public class AsyncTestConfiguration {

    @Bean
    public AnnotationAsyncExecutionAspect annotationAsyncExecutionAspect(BeanFactory beanFactory) {
        AnnotationAsyncExecutionAspect aspect = AnnotationAsyncExecutionAspect.aspectOf();
        aspect.setBeanFactory(beanFactory);
        return aspect;
    }
}
