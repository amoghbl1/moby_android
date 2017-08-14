package info.guardianproject.nearby.bluetooth.activity;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import de.greenrobot.event.EventBus;
import info.guardianproject.nearby.bluetooth.roles.mananger.BluetoothManager;
import info.guardianproject.nearby.bluetooth.bus.BluetoothCommunicator;
import info.guardianproject.nearby.bluetooth.bus.BondedDevice;
import info.guardianproject.nearby.bluetooth.bus.ClientConnectionFail;
import info.guardianproject.nearby.bluetooth.bus.ClientConnectionSuccess;
import info.guardianproject.nearby.bluetooth.bus.ServeurConnectionFail;
import info.guardianproject.nearby.bluetooth.bus.ServeurConnectionSuccess;


/**
 * Created by Rami MARTIN on 13/04/2014.
 */
public abstract class BluetoothFragmentActivity extends FragmentActivity {

    protected BluetoothManager mBluetoothManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBluetoothManager = new BluetoothManager(this);
        checkBluetoothAviability();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this);
        mBluetoothManager.setNbrClientMax(myNbrClientMax());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        closeAllConnexion();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BluetoothManager.REQUEST_DISCOVERABLE_CODE) {
            if (resultCode == BluetoothManager.BLUETOOTH_REQUEST_REFUSED) {
            } else if (resultCode == BluetoothManager.BLUETOOTH_REQUEST_ACCEPTED) {
                onBluetoothStartDiscovery();
            } else {
            }
        }
    }

    public void closeAllConnexion(){
        mBluetoothManager.closeAllConnexion();
    }

    public void checkBluetoothAviability(){
        if(!mBluetoothManager.checkBluetoothAviability()){
            onBluetoothNotAviable();
        }
    }

    public void setTimeDiscoverable(int timeInSec){
        mBluetoothManager.setTimeDiscoverable(timeInSec);
    }

    public void startDiscovery(){

        mBluetoothManager.makeDiscoverable();
    }

    public boolean isConnected(){
        return mBluetoothManager.isConnected;
    }

    public void scanAllBluetoothDevice(){
        mBluetoothManager.scanAllBluetoothDevice();
    }

    public void disconnectClient(){
        mBluetoothManager.stopScanningBluetoothDevices();
        mBluetoothManager.disconnectClient();
    }

    public void disconnectServer(){
        mBluetoothManager.disconnectServer();
    }

    public void createServeur(String address){
        mBluetoothManager.createServeur(address);
    }

    public void selectServerMode(boolean isDiscoverable){
        mBluetoothManager.selectServerMode();
        if (isDiscoverable)
            mBluetoothManager.makeDiscoverable();
      //  mBluetoothManager.createServeur("foo");
    }
    public void selectClientMode(){
        mBluetoothManager.selectClientMode();
    }

    public BluetoothManager.TypeBluetooth getTypeBluetooth(){
        return mBluetoothManager.mType;
    }

    public BluetoothManager.TypeBluetooth getBluetoothMode(){
        return mBluetoothManager.mType;
    }

    public void createClient(String addressMac){
        mBluetoothManager.createClient(addressMac);
    }

    public void sendMessage(String message){
        mBluetoothManager.sendMessage(message);

    }

    public abstract int myNbrClientMax();
    public abstract void onBluetoothDeviceFound(BluetoothDevice device);
    public abstract void onClientConnectionSuccess();
    public abstract void onClientConnectionFail();
    public abstract void onServeurConnectionSuccess();
    public abstract void onServeurConnectionFail();
    public abstract void onBluetoothStartDiscovery();
    public abstract void onBluetoothCommunicator(String messageReceive);
    public abstract void onBluetoothNotAviable();

    public void onEventMainThread(BluetoothDevice device){
        if(!mBluetoothManager.isNbrMaxReached()){
            onBluetoothDeviceFound(device);
            createServeur(device.getAddress());
        }
    }

    public void onEventMainThread(ClientConnectionSuccess event){
        mBluetoothManager.isConnected = true;
        onClientConnectionSuccess();
    }

    public void onEventMainThread(ClientConnectionFail event){
        mBluetoothManager.isConnected = false;
        onClientConnectionFail();
    }

    public void onEventMainThread(ServeurConnectionSuccess event){
        mBluetoothManager.isConnected = true;
        mBluetoothManager.onServerConnectionSuccess(event.mClientAdressConnected);
        onServeurConnectionSuccess();
    }

    public void onEventMainThread(ServeurConnectionFail event){
        mBluetoothManager.onServerConnectionFailed(event.mClientAdressConnectionFail);
        onServeurConnectionFail();
    }

    public void onEventMainThread(BluetoothCommunicator event){
        onBluetoothCommunicator(event.mMessageReceive);
    }

    public void onEventMainThread(BondedDevice event){
        //mBluetoothManager.sendMessage("BondedDevice");
    }
}
