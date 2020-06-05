//import sun.awt.Mutex
import kotlin.concurrent.*
import kotlinx.coroutines.*
//import kotlin.coroutines.*
import kotlinx.coroutines.sync.*
import kotlin.system.*

class thermalInterface{

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

    lateinit var e0 : thermalElement
    lateinit var e1 : thermalElement
}

class signedThermalInterface(thInterface : thermalInterface, sgn : Float){
    var mThermalInterface :thermalInterface = thInterface
    var sign : Float = sgn

    fun computeHeatTransfer(dt : Float, tickNumber : Int):Float{
        mThermalInterface.computeHeatTransfer(dt,tickNumber)
        return sign*mThermalInterface.heatTranfer;
    }
}

class thermalElement{
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

    var themalInterfacesList = ArrayList<signedThermalInterface>()
}

class thermalSimulator{

    suspend fun updateSimulation(dt : Float,tickNumber : Int) = runBlocking{
        for(l in L){
            //println("a")
            val c = launch {
                l.updateT(dt,tickNumber)
            }
            //println("b")
        }
    }

    var L = ArrayList<thermalElement>()
}


fun main() = runBlocking<Unit> {

    var a1 = thermalElement();
    var a2 = thermalElement();
    var i12 = thermalInterface()
    i12.e0 = a1
    i12.e1 = a2
    var si12 = signedThermalInterface(i12,1f)
    var si21 = signedThermalInterface(i12,-1f)
    a1.themalInterfacesList.add(si12)
    a2.themalInterfacesList.add(si21)
    //var a2 = a1;
    a2.C = 1000f;
    a1.T0 = 200f
    a1.T1 = 200f

    //var L = ArrayList<thermalElement>()
    //L.add(a1)
    //L.add(a2)

    var sim = thermalSimulator()
    //sim.L = L
    for(i in 0..100){
        var a = thermalElement()
        a.T0 = 300f + 10f * i
        a.T1 = 300f + 10f * i
        sim.L.add(a)

        //add cpnnection with previous element
        if(i > 0) {
            var i12 = thermalInterface()
            i12.e0 = sim.L[i-1]
            i12.e1 = sim.L[i]

            var si12 = signedThermalInterface(i12,1f)
            var si21 = signedThermalInterface(i12,-1f)
            sim.L[i-1].themalInterfacesList.add(si12)
            sim.L[i].themalInterfacesList.add(si21)
        }
    }

    //var tickNumber = 0
    var dt = 0.05f

    for(tickNumber in 0..10){
        //val c = GlobalScope.launch {
            sim.updateSimulation(dt, tickNumber)
        //}
        //c.join()
        //a1.updateT(dt,tickNumber)
        //a2.updateT(dt,tickNumber)
        println(sim.L[0].getT(0))
        println(sim.L[1].getT(0))
        println("looop ed")
    }
    //a2.getT() = 10f
    //println(a2.getT(0))
    //println(a1.C)
    //println("Hello World!")
}