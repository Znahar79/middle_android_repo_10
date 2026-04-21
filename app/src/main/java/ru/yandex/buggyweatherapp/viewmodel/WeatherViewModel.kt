package ru.yandex.buggyweatherapp.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yandex.buggyweatherapp.model.Location
import ru.yandex.buggyweatherapp.model.WeatherData
import ru.yandex.buggyweatherapp.repository.LocationRepository
import ru.yandex.buggyweatherapp.repository.WeatherRepository
import ru.yandex.buggyweatherapp.utils.ImageLoader
import java.util.Timer
import java.util.TimerTask

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val weatherRepository = WeatherRepository()
    private val locationRepository = LocationRepository(application)

    private val _weatherData = MutableLiveData<WeatherData?>()
    val weatherData: LiveData<WeatherData?> = _weatherData
    private val _currentLocation = MutableLiveData<Location?>()
    val currentLocation: LiveData<Location?> = _currentLocation
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    private val _cityName = MutableLiveData<String?>()
    val cityName: LiveData<String?> = _cityName
    private var refreshTimer: Timer? = null
    
    
    fun initialize() {
        fetchCurrentLocationWeather()
        startAutoRefresh()
    }


    fun fetchCurrentLocationWeather() {
        _isLoading.value = true
        _error.value = ""   // Clear error with an empty string

        locationRepository.getCurrentLocation { location ->
            if (location != null) {
                viewModelScope.launch(Dispatchers.Main) {
                    _currentLocation.value = location

                    val cityNameFromLocation = locationRepository.getCityNameFromLocation(location)
                    _cityName.value = cityNameFromLocation ?: ""  // Fallback to empty string if null

                    // getWeatherForLocation will eventually set _isLoading to false
                    getWeatherForLocation(location)
                }
            } else {
                viewModelScope.launch(Dispatchers.Main) {
                    _isLoading.value = false
                    _error.value = "Unable to get current location"
                }
            }
        }
    }
    
    fun getWeatherForLocation(location: Location) {
        _isLoading.value = true
        _error.value = null
        
        weatherRepository.getWeatherData(location) { data, exception ->
            
            Handler(Looper.getMainLooper()).post {
                _isLoading.value = false
                
                if (data != null) {
                    _weatherData.value = data
                } else {
                    _error.value = exception?.message ?: "Unknown error"
                }
            }
        }
    }
    
    fun searchWeatherByCity(city: String) {
        if (city.isBlank()) {
            _error.value = "City name cannot be empty"
            return
        }
        _isLoading.value = true
        _error.value = null
        
        weatherRepository.getWeatherByCity(city) { data, exception ->
            
            _isLoading.value = false
            
            if (data != null) {
                _weatherData.value = data
                _cityName.value = data.cityName
                _currentLocation.value = Location(0.0, 0.0, data.cityName)
            } else {
                _error.value = exception?.message ?: "Unknown error"
            }
        }
    }
    
    
    fun formatTemperature(temp: Double): String {
        return "${temp.toInt()}°C"
    }
    
    
    fun loadWeatherIcon(iconCode: String) {
        viewModelScope.launch {
            val iconUrl = "https://openweathermap.org/img/wn/$iconCode@2x.png"
            ImageLoader.loadImage(iconUrl)
        }
    }
    
    
    private fun startAutoRefresh() {
        refreshTimer = Timer()
        refreshTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                _currentLocation.value?.let { location ->
                    getWeatherForLocation(location)
                }
            }
        }, 60000, 60000)
    }
    
    
    fun toggleFavorite() {
        _weatherData.value?.let {
            it.isFavorite = !it.isFavorite
            _weatherData.value = it
        }
    }
    
    
    override fun onCleared() {
        refreshTimer?.cancel()
        refreshTimer = null
        super.onCleared()
    }
}