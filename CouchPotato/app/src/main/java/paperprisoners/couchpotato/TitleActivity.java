package paperprisoners.couchpotato;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * The title screen Activity of Couch Potato.
 *
 * @author Ian Donovan
 */
public class TitleActivity extends Activity implements View.OnClickListener, TextWatcher {

    private TextView titleText;
    private EditText nameField;
    private Button submit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Layout setup
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_title);
        //Element setup
        titleText = (TextView) this.findViewById(R.id.title_text);
        nameField = (EditText) this.findViewById(R.id.title_name_field);
        submit = (Button) this.findViewById(R.id.title_submit);
        //Setting fonts
        try {
            Typeface regular = Typeface.createFromAsset( getAssets(), "font/oswald/Oswald-Regular.ttf" );
            Typeface bold = Typeface.createFromAsset( getAssets(), "font/oswald/Oswald-Bold.ttf" );
            titleText.setTypeface(bold);
            nameField.setTypeface(regular);
            submit.setTypeface(bold);
        }
        catch (Exception e) {}
        //Adding listeners
        nameField.addTextChangedListener(this);
        submit.setOnClickListener(this);
        submit.setEnabled(false);
        //Setting values
        String username = this.getIntent().getStringExtra("username");
        if (username != null)
            ((EditText)findViewById(R.id.title_name_field)).setText(username);
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.title_submit) {
            Intent toSelect = new Intent( this, GameSelectActivity.class );
            toSelect.putExtra("username", ((EditText)findViewById(R.id.title_name_field)).getText().toString());
            this.startActivity( toSelect );
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s != null && s.length() >= 1) {
            submit.setEnabled(true);
            submit.setBackgroundColor( ContextCompat.getColor(this, R.color.main_accept) );
            submit.setTextColor( ContextCompat.getColor(this, R.color.main_black) );
        }
        else {
            submit.setEnabled(false);
            submit.setBackgroundColor( ContextCompat.getColor(this, R.color.main_black) );
            submit.setTextColor( ContextCompat.getColor(this, R.color.main_white) );
        }
    }

    @Override
    public void afterTextChanged(Editable s) {

    }
}