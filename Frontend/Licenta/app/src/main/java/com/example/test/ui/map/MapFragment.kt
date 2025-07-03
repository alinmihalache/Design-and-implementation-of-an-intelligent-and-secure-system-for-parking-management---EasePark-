package com.example.test.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.test.R
import com.example.test.auth.AuthManager
import com.example.test.databinding.FragmentMapBinding
import com.example.test.databinding.DialogReservationBinding
import com.example.test.models.ParkingSpot
import com.example.test.models.ParkingSpotStatus
import com.example.test.models.OperationStatus
import com.example.test.utils.ColorUtils
import com.example.test.utils.ParkingSpotUtils
import com.example.test.viewmodel.ParkingViewModel
import com.example.test.viewmodel.ParkingViewModelFactory
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.ArrayAdapter
import java.util.*
import com.example.test.ui.main.ParkingSpotAdapter
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.flow.first
import java.util.TimeZone
import android.util.Log
import com.example.test.repository.ParkingRepository
import com.google.android.gms.location.LocationRequest
import com.example.test.models.Vehicle
import java.text.ParseException

class MapFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var authManager: AuthManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    // Instantiate repository and factory for the ViewModel
    private val repository by lazy { ParkingRepository() }
    private val parkingViewModel: ParkingViewModel by viewModels { ParkingViewModelFactory(repository) }

    private var googleMap: GoogleMap? = null
    private val parkingPolygons = mutableMapOf<String, Polygon>()
    private val displayDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val backendDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    // Variabile pentru a stoca selecțiile din dialog
    private var selectedStartDateTime: Calendar = Calendar.getInstance()
    private var selectedEndDateTime: Calendar = Calendar.getInstance()

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your app.
                enableMyLocation()
            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied.
                Toast.makeText(context, "Location permission is required to find nearby parking spots.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        android.util.Log.d("MapFragment", "onViewCreated called")
        
        // Verificăm starea inițială
        if (authManager.isAuthenticated.value == true) {
            android.util.Log.d("MapFragment", "Initial auth state is true, setting up map")
            setupMap()
        }
        
        // Observe authentication state
        viewLifecycleOwner.lifecycleScope.launch {
            authManager.isAuthenticated.observe(viewLifecycleOwner) { isAuthenticated ->
                android.util.Log.d("MapFragment", "Auth state changed: isAuthenticated = $isAuthenticated")
                if (isAuthenticated) {
                    android.util.Log.d("MapFragment", "User is authenticated, setting up map")
                    setupMap()
                } else {
                    android.util.Log.d("MapFragment", "User is not authenticated, clearing map")
                    googleMap?.clear()
                    parkingPolygons.clear()
                    binding.parkingInfoCard.visibility = View.GONE
                }
            }
        }
    }

    private fun setupMap() {
        android.util.Log.d("MapFragment", "setupMap called")
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        android.util.Log.d("MapFragment", "onMapReady called")
        googleMap = map
        
        // Setăm camera pe Iași imediat
        val iasiLatLng = LatLng(47.15672353105814, 27.604045469707067)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(iasiLatLng, 18f))
        
        enableMyLocation()

        // Set up map click listener
        map.setOnPolygonClickListener { polygon ->
            val spotId = polygon.tag as? Long
            spotId?.let { id ->
                // Get spot directly from the latest value of the UNIFIED flow
                val spot = parkingViewModel.unifiedParkingSpots.value.find { it.id == id }
                spot?.let {
                    showParkingSpotInfo(it)
                }
            }
        }

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        // FAB filter removed - no longer needed
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Observe the NEW unified flow that guarantees consistent data
            parkingViewModel.unifiedParkingSpots.collectLatest { spots ->
                android.util.Log.d("MapFragment", "Received ${spots.size} unified parking spots for map update")
                if (googleMap != null) { // Ensure map is ready before trying to update
                    updateMapPolygons(spots)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            parkingViewModel.reservationStatus.collect { status ->
                when (status) {
                    is OperationStatus.Success -> {
                        Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                        parkingViewModel.resetReservationStatus()
                    }
                    is OperationStatus.Error -> {
                        Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                        parkingViewModel.resetReservationStatus()
                    }
                    is OperationStatus.Loading -> {
                        // Optionally show a loading indicator
                    }
                    else -> {} // Initial state
                }
            }
        }
    }

    private fun updateMapPolygons(spots: List<ParkingSpot>) {
        // Step 1: Clear only the managed polygons to ensure a clean slate for updates.
        parkingPolygons.values.forEach { it.remove() }
        parkingPolygons.clear()

        // Step 2: Regenerate polygon geometries using the existing utility.
        // This is important if spot alignment logic changes.
        val alignedPolygonsData = ParkingSpotUtils.generateAlignedParkingSpots(spots.sortedBy { it.id })
        
        // Step 3: Redraw every single polygon based on the fresh data.
        alignedPolygonsData.forEach { polygonData ->
            val originalSpot = spots.find { it.id == polygonData.spotId } ?: return@forEach
            
            // Use existing methods to determine appearance.
            val displayStatus = originalSpot.getDisplayStatus()
            val color = ColorUtils.getStatusColor(requireContext(), displayStatus)
            
            // Add the newly created polygon to the map.
            val polygon = googleMap?.addPolygon(
                PolygonOptions()
                    .addAll(polygonData.points)
                    .strokeColor(color)
                    .fillColor(color)
                    .strokeWidth(2f)
                    .clickable(true)
            )

            // Track the new polygon for the next update cycle.
            if (polygon != null) {
                polygon.tag = originalSpot.id
                parkingPolygons[originalSpot.id.toString()] = polygon
            }
        }
    }

    private fun showParkingSpotInfo(spot: ParkingSpot) {
        // --- DIAGNOSTIC LOG ---
        // This will print the entire state of the clicked spot to Logcat,
        // allowing us to see exactly what the UI is working with.
        Log.d("MapFragment_SpotInfo", "Spot clicked: $spot")
        // --- END DIAGNOSTIC LOG ---

        binding.parkingInfoCard.visibility = View.VISIBLE
        binding.parkingSpotTitle.text = spot.address

        // Display spot type
        binding.parkingSpotType.text = getString(R.string.spot_type_format, spot.type.toString().lowercase().replaceFirstChar { it.titlecase() })

        val displayStatus = spot.getDisplayStatus()
        val statusText = getStatusText(requireContext(), displayStatus)
        val priceText = getString(R.string.spot_price_format, spot.pricePerHour)
        
        var fullInfoText = "$priceText - $statusText"

        if (displayStatus == ParkingSpotStatus.RESERVED || displayStatus == ParkingSpotStatus.OCCUPIED) {
            val reservation = spot.currentReservation
            if (reservation != null) {
                try {
                    val startDate = backendDateFormat.parse(reservation.startTime)
                    val endDate = backendDateFormat.parse(reservation.endTime)

                    if (startDate != null && endDate != null) {
                        val formattedStart = displayDateFormat.format(startDate)
                        val formattedEnd = displayDateFormat.format(endDate)

                        fullInfoText += "\n" + getString(R.string.spot_reserved_format,
                            reservation.plateNumber,
                            formattedStart,
                            formattedEnd
                        )
                    }
                } catch (e: ParseException) {
                    Log.e("MapFragment", "Error parsing reservation dates", e)
                    // Optionally, show a generic message if parsing fails
                    fullInfoText += "\nPerioada rezervării este invalidă."
                }
            }
        }
        
        binding.parkingSpotInfo.text = fullInfoText

        if (displayStatus == ParkingSpotStatus.AVAILABLE) {
            binding.reserveButton.isEnabled = true
            binding.reserveButton.text = getString(R.string.action_reserve)
            binding.reserveButton.setOnClickListener {
                showReservationDialog(spot)
            }
        } else {
            binding.reserveButton.isEnabled = false
            binding.reserveButton.text = statusText
        }
    }

    private fun getStatusText(context: android.content.Context, status: ParkingSpotStatus): String {
        return when (status) {
            ParkingSpotStatus.AVAILABLE -> context.getString(R.string.status_available)
            ParkingSpotStatus.RESERVED -> context.getString(R.string.status_reserved)
            ParkingSpotStatus.OCCUPIED -> context.getString(R.string.status_occupied)
            ParkingSpotStatus.MAINTENANCE -> context.getString(R.string.status_maintenance)
        }
    }

    private fun showReservationDialog(spot: ParkingSpot) {
        val dialogBinding = DialogReservationBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogBinding.root).create()

        dialogBinding.tvSpotDetails.text = "${spot.address} - ${getString(R.string.spot_price_format, spot.pricePerHour)}"

        // Setup vehicle dropdown by observing the StateFlow from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            parkingViewModel.userVehicles.collectLatest { vehicles ->
                val vehicleStrings = vehicles.map { it.plateNumber }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, vehicleStrings)
                dialogBinding.actVehicle.setAdapter(adapter)
            }
        }

        // Setup date/time pickers and validation logic
        setupDateTimePicker(dialogBinding, spot)

        dialogBinding.btnConfirm.setOnClickListener {
            val selectedPlate = dialogBinding.actVehicle.text.toString()
            val selectedVehicle = parkingViewModel.userVehicles.value.find { it.plateNumber == selectedPlate }

            if (selectedVehicle == null) {
                Toast.makeText(context, getString(R.string.error_no_vehicle_selected), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val startTime = ParkingSpotUtils.formatDateToISO8601(selectedStartDateTime.time)
            val endTime = ParkingSpotUtils.formatDateToISO8601(selectedEndDateTime.time)

            // Correctly call the createReservation function with named arguments
            parkingViewModel.createReservation(
                vehicleId = selectedVehicle.id,
                parkingSpotId = spot.id,
                startTime = startTime,
                endTime = endTime
            )
            dialog.dismiss()
        }

        dialog.show()
    }
    
    private fun setupDateTimePicker(dialogBinding: DialogReservationBinding, spot: ParkingSpot) {
        val now = Calendar.getInstance()
        selectedStartDateTime = (now.clone() as Calendar)
        selectedEndDateTime = (now.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 1) }

        updateDateTimeFields(dialogBinding)
        validateTimeRangeAndShowPrice(dialogBinding, spot)

        dialogBinding.etStartDateTime.setOnClickListener { showDatePicker(dialogBinding, true, spot) }
        dialogBinding.etEndDateTime.setOnClickListener { showDatePicker(dialogBinding, false, spot) }
    }

    private fun showDatePicker(binding: DialogReservationBinding, isStart: Boolean, spot: ParkingSpot) {
        val calendar = if (isStart) selectedStartDateTime else selectedEndDateTime
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            R.style.CustomDatePickerDialogTheme,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                showTimePicker(binding, isStart, spot)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun showTimePicker(binding: DialogReservationBinding, isStart: Boolean, spot: ParkingSpot) {
        val calendar = if (isStart) selectedStartDateTime else selectedEndDateTime
        TimePickerDialog(
            requireContext(),
            R.style.CustomTimePickerDialogTheme,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                if (isStart && !selectedEndDateTime.after(selectedStartDateTime)) {
                    selectedEndDateTime = (selectedStartDateTime.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 1) }
                }
                updateDateTimeFields(binding)
                validateTimeRangeAndShowPrice(binding, spot)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDateTimeFields(dialogBinding: DialogReservationBinding) {
        dialogBinding.etStartDateTime.setText(dateTimeFormat.format(selectedStartDateTime.time))
        dialogBinding.etEndDateTime.setText(dateTimeFormat.format(selectedEndDateTime.time))
    }

    private fun validateTimeRangeAndShowPrice(dialogBinding: DialogReservationBinding, spot: ParkingSpot) {
        dialogBinding.tvTotalPrice.visibility = View.GONE
        dialogBinding.tvTimeError.visibility = View.GONE
        dialogBinding.btnConfirm.isEnabled = false

        // Add a small buffer of a few seconds to avoid race conditions with user input.
        val now = Calendar.getInstance().apply { add(Calendar.SECOND, -30) }

        // 1. New Check: Start time cannot be in the past.
        if (selectedStartDateTime.before(now)) {
            dialogBinding.tvTimeError.text = getString(R.string.error_start_time_in_past)
            dialogBinding.tvTimeError.visibility = View.VISIBLE
            return
        }

        // 2. Existing Check, now with a clearer error message: End time must be after start time.
        if (!selectedEndDateTime.after(selectedStartDateTime)) {
            dialogBinding.tvTimeError.text = getString(R.string.error_end_time_before_start)
            dialogBinding.tvTimeError.visibility = View.VISIBLE
            return
        }

        // If all checks pass, calculate and show the price.
        val durationMillis = selectedEndDateTime.timeInMillis - selectedStartDateTime.timeInMillis
        val hours = durationMillis / (1000.0 * 60 * 60)
        
        if (hours > 0) {
            val pricePerHour = spot.pricePerHour ?: 0.0
            val totalPrice = hours * pricePerHour
            dialogBinding.tvTotalPrice.text = getString(R.string.label_total_price, totalPrice)
            dialogBinding.tvTotalPrice.visibility = View.VISIBLE
            dialogBinding.btnConfirm.isEnabled = true
        } else {
             dialogBinding.tvTotalPrice.visibility = View.GONE
        }
    }

    private fun enableMyLocation() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                googleMap?.isMyLocationEnabled = true
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
                        // Load parking spots based on the user's last known location
                        parkingViewModel.loadParkingSpots(it.latitude, it.longitude)
                    }
                }
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {

                Toast.makeText(context, "Location permission is needed to show your position.", Toast.LENGTH_LONG).show()
                locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                // Directly ask for the permission.
                locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    override fun onResume() {
        super.onResume()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 