package com.ammar.filescenter.activities.MainActivity;

import android.app.UiModeManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.FragmentTransaction;

import com.ammar.filescenter.R;
import com.ammar.filescenter.activities.MainActivity.fragments.SendFragment;
import com.ammar.filescenter.activities.MainActivity.fragments.SettingsFragment;
import com.ammar.filescenter.services.NetworkService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private FloatingActionButton serverButton;
    private BottomNavigationView bottomNavigationView;

    public static boolean darkMode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        prepareActivity();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initItems();
        setItemsListener();
        initStates();
        observeStates();
    }

    public void prepareActivity() {
        SharedPreferences settingsPref = getSharedPreferences(SettingsFragment.SettingsPrefFile, MODE_PRIVATE);
        // check for first Run
        SharedPreferences firstRunPref = getSharedPreferences("FirstRun", MODE_PRIVATE);
        boolean isFirstRun = firstRunPref.getBoolean("firstrun", true);
        if (isFirstRun) {
            // Download folder will be the default.
            String downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();

            settingsPref.edit()
                    .putBoolean(SettingsFragment.DarkModeKey, true)
                    .putString(SettingsFragment.UploadDir, downloadFolder)
                    .apply();

            firstRunPref.edit().putBoolean("firstrun", false).apply();
        }

        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        darkMode = settingsPref.getBoolean(SettingsFragment.DarkModeKey, true);
        AppCompatDelegate.setDefaultNightMode(darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void initItems() {
        bottomNavigationView = findViewById(R.id.BottomNavView);
        serverButton = findViewById(R.id.FAB_ServerButton);
        bottomNavigationView.setSelectedItemId(R.id.B_Share);
    }


    private int currentFragmentIndex = 1;
    private final ArrayList<Class> fragments = new ArrayList<>(Arrays.asList(new Class[]{
            SendFragment.class,
            SettingsFragment.class
    }));


    private void setItemsListener() {
        bottomNavigationView.setOnItemSelectedListener(item -> {

            int id = item.getItemId();
            FragmentTransaction ft = getSupportFragmentManager()
                    .beginTransaction();

            int nextFragmentIndex = -1;
            if (id == R.id.B_Share) {
                nextFragmentIndex = fragments.indexOf(SendFragment.class);
                if (currentFragmentIndex < nextFragmentIndex) {
                    ft.setCustomAnimations(R.anim.fragment_enter_left, R.anim.fragment_exit_left);
                }
                ft.replace(R.id.MainActivityFragmentContainer, SendFragment.class, null);
            } else if (id == R.id.B_Settings) {
                nextFragmentIndex = fragments.indexOf(SendFragment.class);
                if (currentFragmentIndex < nextFragmentIndex) {
                    ft.setCustomAnimations(R.anim.fragment_enter_left, R.anim.fragment_exit_left);
                }
                ft.replace(R.id.MainActivityFragmentContainer, SettingsFragment.class, null);
            }
            currentFragmentIndex = nextFragmentIndex;
            ft.commit();
            return true;
        });
        serverButton.setOnClickListener((button) -> {
            Intent serviceIntent = new Intent(this, NetworkService.class);
            serviceIntent.setAction(NetworkService.ACTION_TOGGLE_SERVER);
            startService(serviceIntent);
        });
    }

    private void initStates() {
        Intent serviceIntent = new Intent(this, NetworkService.class);
        serviceIntent.setAction(NetworkService.ACTION_GET_SERVER_STATUS);
        startService(serviceIntent);
    }

    private void observeStates() {
        NetworkService.serverStatusObserver.observe(this, running -> {
            if (running) {
                serverButton.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.status_on)));
            } else {
                serverButton.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.status_off)));
            }
        });
    }

}