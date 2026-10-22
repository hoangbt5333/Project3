package com.example.testcamera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class KioskHomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_kiosk_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnStart = view.findViewById<Button>(R.id.btnStartAuthentication)
        btnStart.setOnClickListener {
            (activity as? MainActivity)?.navigateToFragment(KioskCameraFragment(), addToBackStack = false)
        }
    }
}
