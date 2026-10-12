package com.pronoscore360.core.util

import com.pronoscore360.core.model.SelectionItemIn

data class BasketItem(val fixtureId: Long, val key: String, val label: String, val match: String, val probability: Double)

/** Cesta de selecciones en edición (lógica pura, testeable). Sin duplicados y con tope configurable por plan. */
class SelectionBasket(private val max: Int = 12, initial: List<BasketItem> = emptyList()) {
    private val _items = initial.toMutableList()
    val items: List<BasketItem> get() = _items.toList()
    fun has(fixtureId: Long, key: String) = _items.any { it.fixtureId == fixtureId && it.key == key }
    /** @return false si ya existía o se alcanzó el tope. */
    fun add(i: BasketItem): Boolean { if (has(i.fixtureId, i.key) || _items.size >= max) return false; _items += i; return true }
    fun remove(fixtureId: Long, key: String) { _items.removeAll { it.fixtureId == fixtureId && it.key == key } }
    fun clear() = _items.clear()
    fun toRequest() = _items.map { SelectionItemIn(it.fixtureId, it.key) }
}
