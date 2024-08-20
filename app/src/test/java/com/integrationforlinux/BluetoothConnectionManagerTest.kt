package com.integrationforlinux

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import junit.framework.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.util.UUID


class BluetoothConnectionManagerTest {
    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockBluetoothAdapter: BluetoothAdapter

    @Mock
    private lateinit var mockBluetoothServerSocket: BluetoothServerSocket

    @Mock
    private lateinit var mockBluetoothSocket: BluetoothSocket

    @Mock
    private lateinit var mockBluetoothDevice: BluetoothDevice

    private lateinit var bluetoothConnectionManager: BluetoothConnectionManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        bluetoothConnectionManager = BluetoothConnectionManager(mockContext)
        `when`(mockContext.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(mockBluetoothAdapter)
        `when`(mockBluetoothAdapter.listenUsingRfcommWithServiceRecord(anyString(), any(UUID::class.java)))
            .thenReturn(mockBluetoothServerSocket)
    }

    @Test
    fun testStartServer_withBluetoothPermissionGranted() {
        // Simula a permissão concedida
        `when`(ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_CONNECT))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        // Simula o comportamento de aceitação do socket
        `when`(mockBluetoothServerSocket.accept()).thenReturn(mockBluetoothSocket)

        bluetoothConnectionManager.startServer()

        verify(mockBluetoothServerSocket, times(1)).accept()
        assertNotNull(bluetoothConnectionManager)
    }

    @Test
    fun testStartServer_withoutBluetoothPermissionGranted() {
        // Simula a permissão não concedida
        `when`(ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_CONNECT))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        bluetoothConnectionManager.startServer()

        // Verifica que o método de solicitação de permissão foi chamado
        verify(mockContext as Activity, times(1)).requestPermissions(
            any(Array<String>::class.java), eq(1)
        )
    }

    @Test
    fun testStartClient_withBluetoothPermissionGranted() {
        // Simula a permissão concedida
        `when`(ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_CONNECT))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        bluetoothConnectionManager.startClient(mockBluetoothDevice)

        verify(mockBluetoothSocket, times(1)).connect()
    }

    @Test
    fun testStartClient_withoutBluetoothPermissionGranted() {
        // Simula a permissão não concedida
        `when`(ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_CONNECT))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        bluetoothConnectionManager.startClient(mockBluetoothDevice)

        // Verifica que nenhuma tentativa de conexão foi feita
        verify(mockBluetoothSocket, never()).connect()
    }
}