package com.hackupc.uoe.jobcam.Components

data class Boundary (val label: String,
                     val centre_x: Float,
                     val centre_y: Float,
                     val radius: Float)

data class MLresponse (val results: Array<MLresults>)

data class MLresults (val bbox: DoubleArray,
                      val classes: Array<String>)