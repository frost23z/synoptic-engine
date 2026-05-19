package com.synopticengine.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableAspectJAutoProxy
// Set Spring's @Transactional advice order to 0 (very high precedence / outermost)
// so it opens the transaction before any other application aspect runs. The
// RlsTenantGucAspect needs to be inside the transaction to issue SET LOCAL; with
// the default LOWEST_PRECEDENCE, @Transactional was innermost and our aspect ran
// before any transaction existed.
@EnableTransactionManagement(order = 0)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
