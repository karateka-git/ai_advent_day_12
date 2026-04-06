package agent.memory.layer

import agent.memory.model.MemoryNote

/**
 * Определяет, как объединять существующие и новые заметки внутри одного слоя памяти.
 */
interface MemoryNoteMergePolicy {
    /**
     * Объединяет существующие и новые заметки по правилам конкретной категории.
     *
     * @param existing текущие заметки слоя.
     * @param additions новые заметки, извлечённые из последнего сообщения.
     * @return итоговый набор заметок после объединения.
     */
    fun merge(existing: List<MemoryNote>, additions: List<MemoryNote>): List<MemoryNote>
}

/**
 * Базовая merge policy для durable memory.
 *
 * Сохраняет несколько заметок в одной категории и отфильтровывает только точные дубли
 * в пределах одинаковых `category`, `ownerType`, `ownerId` и `content`.
 */
class RuleBasedMemoryNoteMergePolicy : MemoryNoteMergePolicy {
    /**
     * Добавляет новые заметки поверх существующих и пропускает только точные дубли.
     *
     * @param existing текущие заметки слоя.
     * @param additions новые заметки, полученные от распределителя памяти.
     * @return итоговый набор заметок после объединения.
     */
    override fun merge(existing: List<MemoryNote>, additions: List<MemoryNote>): List<MemoryNote> =
        additions.fold(existing) { current, addition ->
            if (
                current.any {
                    it.category == addition.category &&
                        it.ownerType == addition.ownerType &&
                        it.ownerId == addition.ownerId &&
                        it.content.equals(addition.content, ignoreCase = true)
                }
            ) {
                current
            } else {
                current + addition
            }
        }
}
