package com.hmsoft.pentaxgallery.ui.preferences;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.hmsoft.pentaxgallery.R;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class PreferencesActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        FragmentManager.OnBackStackChangedListener {


    public static void start(Activity activity) {
        Intent i = new Intent(activity, PreferencesActivity.class);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new PreferencesFragment())
                .commit();

        getSupportFragmentManager().addOnBackStackChangedListener(this);
        setTitle(R.string.setting_title);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new Fragment
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());

        setTitle(pref.getTitle());

        args.putString("key", pref.getKey());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();

        return true;
    }

    @Override
    public void onBackStackChanged() {
        List<Fragment>  fragments = getSupportFragmentManager().getFragments();
        if(fragments.size() == 1 && fragments.get(0) instanceof PreferencesFragment) {
            setTitle(R.string.setting_title);
        }
    }
}
