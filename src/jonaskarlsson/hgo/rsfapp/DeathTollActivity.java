package jonaskarlsson.hgo.rsfapp;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.text.util.Linkify;
import android.text.util.Linkify.MatchFilter;
import android.text.util.Linkify.TransformFilter;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Displays and handles the message that tells the user about how many
 * journalists that have been killed the current year.
 * 
 * @author Jonas Karlsson
 * 
 */
public class DeathTollActivity extends Activity {
	private Button mButtonClose;

	/*
	 * Called when the activity starts. Displays a hyperlink to a Internet
	 * address and adds a button that closes the activity. (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		Context context = getApplicationContext();
		CharSequence text = "Gave over!";
		int duration = Toast.LENGTH_LONG;

		Toast toast = Toast.makeText(context, text, duration);
		toast.show();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.death_toll);
		((TextView) this.findViewById(R.id.death_toll))
				.setText(String
						.format("This year %s journalists have been killed. To learn more, click here.",
								this.getDeathToll()));

		MatchFilter matchFilter = new MatchFilter() {
			public final boolean acceptMatch(CharSequence s, int start, int end) {

				return true;
			}
		};
		TransformFilter transformFilter = new TransformFilter() {
			public final String transformUrl(final Matcher match, String url) {
				return "en.rsf.org";
			}
		};

		Pattern pattern = Pattern.compile("here");
		String scheme = "http://";
		Linkify.addLinks(((TextView) this.findViewById(R.id.death_toll)),
				pattern, scheme, matchFilter, transformFilter);

		mButtonClose = (Button) findViewById(R.id.button1);
		mButtonClose.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				finish();
				Context context = getBaseContext();
				Intent i = new Intent(context, RsfApp.class);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(i);
			}
		});

	}

	/**
	 * Fetches the number of journalists killed during the current year. Gets
	 * the figure from the RSF.ORG web site.
	 * 
	 * @return the number of journalists killed
	 */
	private String getDeathToll() {
		try {
			ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

			if (cm.getActiveNetworkInfo() == null) {
				return "many";
			}

			StringBuilder sb = new StringBuilder();
			URL url = new URL(
					"http://en.rsf.org/press-freedom-barometer-journalists-killed.html");
			InputStream stream = new BufferedInputStream(url.openStream());
			int current;
			while ((current = stream.read()) != -1)
				sb.append((char) current);
			String content = sb.toString();

			Pattern pattern = Pattern
					.compile("\\d+\\s*:\\s*(\\d+)\\s+Journalists killed");
			Matcher matcher = pattern.matcher(content);
			matcher.find();
			return matcher.group(1);
		} catch (Exception e) {
			e.printStackTrace();
			return "many";
		}
	}
}
