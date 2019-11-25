/*
Bus Tracker

-this application keep tracks the buses in Halifax
-the positions of the buses are showned on the Map
-the markers on the map updates once per 15 secs
-the user can find his/her position on the map by clicking the icon at top-right corner
-tried to use the sharedPrefences to save the current state of Map camera, store in the onStop, retrieve it in the onMapReady
 but ends up that the camera will point to Venezuela every time for reason unkown,
 definitely gonna figure this out in the future
-so set the camera position which pointing to Dalhousie as Default
-also tried to implement the Alert, using a simliar fashion such as vehicle position, but the entity I ve read seems to be null objects

This is entirely my own work

Author: Qu Yuze
Last Modified: 2019/11/22

Dalhousie Univeristy
 */


package ca.dal.cs.cs4176.yqu.bustracker

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.nfc.Tag
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.OnSuccessListener
import java.net.URL;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import java.net.MalformedURLException
import java.util.*
import kotlin.collections.HashMap

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    val TAG = "MapsAcitvity"
    private lateinit var mMap: GoogleMap
    private lateinit var currLocation : Location
    private lateinit var fusedLocationClient : FusedLocationProviderClient
    private lateinit var locationRequest : LocationRequest
    private lateinit var locationCallback : LocationCallback
    private lateinit var timer: Timer
    private val noDelayed = 0L
    private val everyFifteenSeconds = 15000L
    private lateinit var sharedPreferences: SharedPreferences
    private var currentMapCenterLat : Double = 0.toDouble()
    private var currentMapCenterLong : Double = 0.toDouble()
    private var currentMapZoom : Float = 15.toFloat()
    var locationButton : Button?= null
    var alertView: TextView?  = null
    var mMarkers : HashMap<String, Marker> = HashMap()
    var currentMarker : Marker? = null
    private lateinit var originalIcon: Bitmap
    private lateinit var resizedIcon: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationButton = findViewById(R.id.locationButton)
        originalIcon = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.bus_icon3)
        resizedIcon = Bitmap.createScaledBitmap(originalIcon, 90, 90, false)
        sharedPreferences= getSharedPreferences("p", Context.MODE_PRIVATE)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        searchPosition()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    currLocation = location
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        var editor: SharedPreferences.Editor = sharedPreferences.edit();
        editor.putFloat("Latitude", mMap.cameraPosition.target.latitude.toFloat());
        editor.putFloat("Longitude", mMap.cameraPosition.target.longitude.toFloat());
        editor.putFloat("zoom", mMap.cameraPosition.zoom);
        editor.commit()

    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentMapCenterLat = sharedPreferences.getFloat("Latitude", 44.6389854.toFloat()).toDouble()
        currentMapCenterLong = sharedPreferences.getFloat("Longitude",  -63.59296.toFloat()).toDouble()
        currentMapZoom = sharedPreferences.getFloat("zoom", 20.toFloat())

    }


    public override fun onStart() {
        super.onStart()

        createLocationRequest()
        if(!hasLocationPermissions()){
            ActivityCompat.requestPermissions(this@MapsActivity,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_PERMISSION_REQUEST_CODE
            )
        }else{
            getAddress()
        }
    }

    fun onLocateButtonClick(v: View){
        moveTheCameraToCurrentLocation()
    }

    fun moveTheCameraToCurrentLocation(){
        if(currLocation != null){
            currentMapCenterLat = currLocation.latitude
            currentMapCenterLong = currLocation.longitude
            val position = LatLng(currentMapCenterLat, currentMapCenterLong)
            val center : CameraUpdate = CameraUpdateFactory.newLatLng(position)
            val zoom : CameraUpdate = CameraUpdateFactory.zoomTo(15.toFloat())
            mMap.moveCamera(center)
            mMap.animateCamera(zoom)
            if (currentMarker == null){
                currentMarker = mMap.addMarker(MarkerOptions().position(position).title("Your Current Position"))
            }else{
                currentMarker?.remove()
                currentMarker = mMap.addMarker(MarkerOptions().position(position).title("Your Current Position"))
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode != REQUEST_PERMISSION_REQUEST_CODE) return
        if (!grantResults.isEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            getAddress()
        }
    }

    fun getAddress(){
        fusedLocationClient.lastLocation.addOnSuccessListener(this, OnSuccessListener{
                location ->
            if (location != null){
                location.getLatitude()
                location.getLongitude()
            }
        }).addOnFailureListener(this){ e -> Log.w(TAG, "getLastLocation:onFailure", e)}
    }

    private fun createLocationRequest(){
        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun startLocationUpdates(){
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback,
            Looper.getMainLooper())
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()

        val timerTask = object : TimerTask(){
            override fun run() {
                searchPosition()
                updateAlert()
            }
        }
        timer = Timer()
        timer.schedule(timerTask, noDelayed, everyFifteenSeconds)

    }
    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        timer.cancel()
        timer.purge()
    }

    override fun onRestart() {
        super.onRestart()
    }

    override fun onStop() {
        super.onStop()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun hasFineLocationPermission() = ActivityCompat.checkSelfPermission(this,
        android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasCoarseLocationPermission() = ActivityCompat.checkSelfPermission(this,
        android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val REQUEST_PERMISSION_REQUEST_CODE = 34

    fun hasLocationPermissions() = hasFineLocationPermission() && hasCoarseLocationPermission()

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        currentMapCenterLat = sharedPreferences.getFloat("Latitude", 44.6389854.toFloat()).toDouble()
        currentMapCenterLong = sharedPreferences.getFloat("Longitude",  -63.59296.toFloat()).toDouble()
        currentMapZoom = sharedPreferences.getFloat("zoom", 20.toFloat())


        val position = LatLng(currentMapCenterLat,currentMapCenterLong)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15.toFloat()))

    }

    fun searchPosition(){
        var task = VehiclePositionReader().execute()
    }

    fun updateAlert(){
        var task = AlertReader().execute()
    }

    inner class VehiclePositionReader : AsyncTask<Void, Void, List<FeedEntity>>() {
        var url: URL = URL("http://gtfs.halifax.ca/realtime/Vehicle/VehiclePositions.pb")
        override fun doInBackground(vararg params: Void?): List<FeedEntity> {
            try{
                var list : List<FeedEntity> = FeedMessage. parseFrom (url.openStream()).getEntityList()
                return list
            }catch (e : MalformedURLException){
                throw e
            }
        }

        override fun onPostExecute(feedEntity: List<FeedEntity>){

            for(entity : FeedEntity in feedEntity){
                val busId = entity.vehicle.trip.routeId
                val latitude = entity.vehicle.position.latitude
                val longitude = entity.vehicle.position.longitude
                val position = LatLng(latitude.toDouble(), longitude.toDouble())
                if(mMarkers != null){
                    if (mMarkers.containsKey(busId)){

                        var bitmap: Bitmap = resizedIcon.copy(resizedIcon.config, true)
                        var paint: Paint = Paint()
                        paint.setColor(Color.MAGENTA)
                        paint.setTextSize(35.toFloat())
                        var canvas: Canvas = Canvas(bitmap)
                        canvas.drawText(busId.toString(), 30.toFloat(), 50.toFloat(), paint)
                        canvas.translate(0.toFloat(), 200.toFloat())

                        var marker: Marker = mMarkers.get(busId)!!
                        marker.remove()
                        marker = mMap.addMarker(MarkerOptions()
                            .position(position)
                            .title(busId)
                            .icon(BitmapDescriptorFactory.fromBitmap(bitmap)))
                        mMarkers.put(busId, marker)
                    }else{

                        var bitmap: Bitmap = resizedIcon.copy(resizedIcon.config, true)
                        var paint: Paint = Paint()
                        paint.setColor(Color.MAGENTA)
                        paint.setTextSize(35.toFloat())
                        var canvas: Canvas = Canvas(bitmap)
                        canvas.drawText(busId.toString(), 30.toFloat(), 50.toFloat(), paint)
                        canvas.translate(0.toFloat(), 200.toFloat())


                        var marker : Marker = mMap.addMarker(MarkerOptions()
                            .position(position)
                            .title(busId)
                            .icon(BitmapDescriptorFactory.fromBitmap(bitmap)))
                        mMarkers.put(busId, marker)
                    }
                }else{

                    var bitmap: Bitmap = resizedIcon.copy(resizedIcon.config, true)
                    var paint: Paint = Paint()
                    paint.setColor(Color.MAGENTA)
                    paint.setTextSize(35.toFloat())
                    var canvas: Canvas = Canvas(bitmap)
                    canvas.drawText(busId.toString(), 30.toFloat(), 50.toFloat(), paint)
                    canvas.translate(0.toFloat(), 200.toFloat())

                    var marker : Marker = mMap.addMarker(MarkerOptions()
                        .position(position)
                        .title(busId)
                        .icon(BitmapDescriptorFactory.fromBitmap(bitmap)))
                    mMarkers.put(busId, marker)
                }
            }
        }

    }

    inner class AlertReader : AsyncTask<Void, Void, List<FeedEntity>>() {
        var url: URL = URL("http://gtfs.halifax.ca/realtime/Vehicle/VehiclePositions.pb")
        override fun doInBackground(vararg params: Void?): List<FeedEntity> {
            try{
                var list : List<FeedEntity> = FeedMessage. parseFrom (url.openStream()).getEntityList()
                return list
            }catch (e : MalformedURLException){
                throw e
            }
        }

        override fun onPostExecute(feedEntity: List<FeedEntity>){

            for(entity : FeedEntity in feedEntity){
                val cause = entity.alert.cause.toString()
                val effect = entity.alert.effect.toString()
                val text = entity.alert.descriptionText
                val alert = "Alert: " + entity.id + text
                Log.d(TAG, alert)

            }
        }

    }



}
