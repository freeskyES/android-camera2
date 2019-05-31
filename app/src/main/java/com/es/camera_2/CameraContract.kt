package com.es.camera_2

interface CameraContract {

    interface View{

    }

    interface Presenter {

        fun setView(view: View)

        fun onClickCaptureButton()

        fun onClickSwitchButton()

        fun onClickFlashButton()

    }
}