package org.eln2.mc.common.cells.foundation

import org.eln2.mc.data.DataAccessNode
import org.eln2.mc.data.IDataEntity

interface ICellBehavior {
    fun onAdded(container: CellBehaviorContainer)

    fun subscribe(subscribers: SubscriberCollection)

    fun destroy(subscribers: SubscriberCollection)
}

class CellBehaviorContainer(private val cell: CellBase) : IDataEntity {
    val behaviors = ArrayList<ICellBehavior>()

    fun process(action: ((ICellBehavior) -> Unit)) {
        behaviors.forEach(action)
    }

    inline fun <reified T : ICellBehavior> getBehaviorOrNull(): T? {
        return behaviors.first { it is T } as? T
    }

    inline fun <reified T : ICellBehavior> getBehavior(): T {
        return getBehaviorOrNull() ?: error("Failed to get behavior")
    }

    inline fun <reified T : ICellBehavior> add(behavior: T): CellBehaviorContainer {
        if(behaviors.any { it is T }){
            error("Duplicate add behavior $behavior")
        }

        behaviors.add(behavior)

        if(behavior is IDataEntity) {
            dataAccessNode.withChild(behavior.dataAccessNode)
        }

        behavior.onAdded(this)

        return this
    }

    fun changeGraph(){
        behaviors.forEach { it.subscribe(cell.graph.subscribers) }
    }

    fun destroy() {
        behaviors.forEach {
            if(it is IDataEntity) {
                dataAccessNode.children.removeIf { access -> access == it.dataAccessNode }
            }

            it.destroy(cell.graph.subscribers)
        }
    }

    override val dataAccessNode: DataAccessNode = DataAccessNode()
}
