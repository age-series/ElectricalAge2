import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlin.math.*

class ThermalInterface{

    var mutex = Mutex()
    var R = 1f //heat resistance at the interface
    var heatTranfer = 0f //heat transfer during a simulation tick through this interface

    var lastTickComputed = 0 //number of the last tick where the heat transfer was computed. Tick number switch between 0 and 1. This is here to prevent computing twice the heat trasnfered when not needed


    fun computeHeatTransfer(dt : Float, tickNumber : Int){
        var T0 = e0.getT(tickNumber)
        var T1 = e1.getT(tickNumber)
        if(tickNumber != lastTickComputed) {
            heatTranfer = (T1 - T0) / R * dt
            lastTickComputed = tickNumber
        }
    }

    lateinit var e0 : ThermalElement
    lateinit var e1 : ThermalElement
}

class SignedThermalInterface(thInterface : ThermalInterface, sgn : Float){
    var mThermalInterface :ThermalInterface = thInterface
    var sign : Float = sgn

    fun computeHeatTransfer(dt : Float, tickNumber : Int):Float{
        mThermalInterface.computeHeatTransfer(dt,tickNumber)
        return sign*mThermalInterface.heatTranfer;
    }
}

class ThermalElement{
    var C = 1f; //Heat capacity of a given element
    var P = 0f; //Power dissipated as heat in the thermal element (from electrical circuit)

    //temperature needs to be stored twice: at time $t$, we use T0, then we use that to
    //compute temperature at time $t+dt$ that we store in T1, then for $t + dt+dt$ we
    //store it in T0 again etc
    var T0 = 300f
    var T1 = 300f
    //var useT0T1 = 0

    fun getT(tickNumber: Int):Float{
        if(tickNumber.rem(2) == 0){
            return T0
        }
        else{
            return T1
        }
    }

    suspend fun updateT(dt : Float, tickNumber: Int){
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
//helper class that creates thermal elements / interfaces from real world physical values
class ThermalBuilder{

    fun createWireThermalElement(length: Float, radius : Float):ThermalElement{
        var wire = ThermalElement()

        var volume = length * PI * radius*radius
        var volumicMass = 8940f //copper volumic mass in kg/m^3
        var mass = volume * volumicMass
        var heatCapacityPerKilogram = 385f //copper heat capacity per Kg in J/Kg/K

        wire.C = (mass*heatCapacityPerKilogram).toFloat()
        println("C")
        println(wire.C)
        return wire
    }
}


class ThermalSimulator{

    suspend fun updateSimulation(dt : Float,tickNumber : Int) = runBlocking{
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


fun main() = runBlocking<Unit> {


    var sim = ThermalSimulator()

    var builder = ThermalBuilder()
    //sim.L = L
    for(i in 0..100){
        var a = builder.createWireThermalElement(1f,0.01f)//ThermalElement()
        a.T0 = 300f + 10f * i
        a.T1 = 300f + 10f * i
        sim.L.add(a)

        //add cpnnection with previous element
        if(i > 0) {
            var i12 = ThermalInterface()
            i12.e0 = sim.L[i-1]
            i12.e1 = sim.L[i]

            var si12 = SignedThermalInterface(i12,1f)
            var si21 = SignedThermalInterface(i12,-1f)
            sim.L[i-1].themalInterfacesList.add(si12)
            sim.L[i].themalInterfacesList.add(si21)
        }
    }

    //var tickNumber = 0
    var dt = 0.05f

    for(tickNumber in 0..10){
        sim.updateSimulation(dt, tickNumber)
        println(sim.L[0].getT(0))
        println(sim.L[1].getT(0))
        println("looop ed")
    }

}