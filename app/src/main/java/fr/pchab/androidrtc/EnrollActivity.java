package fr.pchab.androidrtc;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class EnrollActivity extends AppCompatActivity {

    private EditText phoneNumberEditText;
    private Button btnEnroll;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enroll);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        editor = sharedPreferences.edit();

        btnEnroll = findViewById(R.id.btnEnroll);
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText);

        if (sharedPreferences.getString("phoneNumber", null) != null) {
            navigateToRtcActivity();
        }

        btnEnroll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (phoneNumberEditText.getText().toString().equals("") || phoneNumberEditText.getText().toString().isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Please input phone number", Toast.LENGTH_LONG).show();
                    return;
                }

                editor.putString("phoneNumber", phoneNumberEditText.getText().toString());
                editor.commit();
                navigateToRtcActivity();
            }
        });
    }

    private void navigateToRtcActivity() {
        Intent intent = new Intent(EnrollActivity.this, RtcActivity.class);
        startActivity(intent);
    }
}
