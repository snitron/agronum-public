package ru.agronum.ewcidshipping

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.web.servlet.config.annotation.EnableWebMvc

@EnableWebMvc
@EnableJpaAuditing
@SpringBootApplication
class EwcidShippingApplication

fun main(args: Array<String>) {
    runApplication<EwcidShippingApplication>(*args)
}
