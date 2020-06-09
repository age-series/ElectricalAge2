import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*


/**
 * represents a thermal interface between two objects. At each tick, the flow can be marked as computed.
 */
class ThermalInterface {

    var mutex = Mutex()
    var R = 1.0 // heat resistance at the interface
    var heatTranfer = 0.0 // heat transfer during a simulation tick through this interface

    var lastTickComputed = 0 // number of the last tick where the heat transfer was computed. Tick number switch between 0 and 1. This is here to prevent computing twice the heat trasnfered when not needed

    /**
     * compute the heat transfer from object 0 to object 1 during time dt for a given tick.
     * If the flow was already computed, use the stored value.
     */
    fun computeHeatTransfer(dt: Double, tickNumber: Int) {
        var T0 = e0.getT(tickNumber)
        var T1 = e1.getT(tickNumber)
        if(tickNumber != lastTickComputed) {
            heatTranfer = (T1 - T0) / R * dt
            lastTickComputed = tickNumber
        }
    }

    lateinit var e0: ThermalElement
    lateinit var e1: ThermalElement
}

/**
 * a thermal interface computes the flow from object 0 to 1. Adding a sign to it allow us
 * to represent the flow from 1 to 0 without having to compute it twice
 */
class SignedThermalInterface(thInterface: ThermalInterface, sgn: Double) {
    var mThermalInterface: ThermalInterface = thInterface
    val sign: Double = sgn

    /**
     * compute the heat transfer during time dt for a given tick
     */
    fun computeHeatTransfer (dt : Double, tickNumber : Int):Double{
        mThermalInterface.computeHeatTransfer(dt, tickNumber)
        return sign * mThermalInterface.heatTranfer;
    }
}

/**
 * a ThermalElement represents a physical object with a given temperature and thermal capacity.
 * It also posses a list of signed thermal interfaces.
 */
class ThermalElement {
    var C = 1.0 // Heat capacity of a given element
    var P = 0.0 // Power dissipated as heat in the thermal element (from electrical circuit)

    // temperature needs to be stored twice: at time $t$, we use T0, then we use that to
    // compute temperature at time $t+dt$ that we store in T1, then for $t + dt+dt$ we
    // store it in T0 again etc
    var T0 = 300.0 //in Kelvin
    var T1 = 300.0
    //var useT0T1 = 0

    /**
     * returns the temperature for the current tick
     */
    fun getT(tickNumber: Int):Double {
        if(tickNumber.rem(2) == 0){
            return T0
        }
        else{
            return T1
        }
    }

    /**
     * Update the temperature for the current tick for an update in time of dt.
     */
    suspend fun updateT(dt: Double, tickNumber: Int) {
        //println("c")
        for (signedInterface in themalInterfacesList) {
            signedInterface.mThermalInterface.mutex.withLock {
                var J = signedInterface.computeHeatTransfer(dt, tickNumber)
                if (tickNumber.rem(2) == 0) {
                    T1 = T1 + J / C + P * dt
                } else {
                    T0 = T0 + J / C + P * dt
                }
            }
        }
    }

    var themalInterfacesList = ArrayList<SignedThermalInterface>()
}

//TODO: change name?
/**
 * helper class that creates thermal elements / interfaces from real world physical values
 */
class ThermalBuilder {

    /**
     * creates a thermal element corresponding to a copper Wire
     */
    fun createWireThermalElement(length: Double, radius: Double):ThermalElement {
        var wire = ThermalElement()

        var volume = length * PI * radius*radius
        var volumicMass = 8940.0 //copper volumic mass in kg/m^3
        var mass = volume * volumicMass
        var heatCapacityPerKilogram = 385.0 //copper heat capacity per Kg in J/Kg/K

        wire.C = mass*heatCapacityPerKilogram
        println("C")
        println(wire.C)
        return wire
    }
}


/**
 * main thermal simulation class
 */
class ThermalSimulator {

    /**
     * update the thermal simulation (i.e. every thermal element). Should be parellel with coroutines.
     */
    suspend fun updateSimulation(dt: Double,tickNumber: Int) = runBlocking{
        for(l in L){
            //println("a")
            val c = launch {
                l.updateT(dt,tickNumber)
            }
            //println("b")
        }
    }

    var L = ArrayList<ThermalElement>()
}

/**
 * test function
 */
fun main() = runBlocking<Unit> {


    var sim = ThermalSimulator()

    var builder = ThermalBuilder()
    for(i in 0..100){
        var a = builder.createWireThermalElement(1.0,0.01)//ThermalElement()
        a.T0 = 300.0 + 10.0 * i
        a.T1 = 300.0 + 10.0 * i
        sim.L.add(a)

        // add cpnnection with previous element
        if(i > 0) {
            var i12 = ThermalInterface()
            i12.e0 = sim.L[i-1]
            i12.e1 = sim.L[i]

            var si12 = SignedThermalInterface(i12,1.0)
            var si21 = SignedThermalInterface(i12,-1.0)
            sim.L[i-1].themalInterfacesList.add(si12)
            sim.L[i].themalInterfacesList.add(si21)
        }
    }

    var dt = 0.05

    for(tickNumber in 0..10){
        sim.updateSimulation(dt, tickNumber)
        println(sim.L[0].getT(0))
        println(sim.L[1].getT(0))
        println("looop ed")
    }

}