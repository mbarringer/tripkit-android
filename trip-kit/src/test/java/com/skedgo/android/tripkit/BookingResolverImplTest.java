package com.skedgo.android.tripkit;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.skedgo.android.common.model.Location;
import com.skedgo.android.common.model.TripSegment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.functions.Func1;
import rx.observers.TestSubscriber;

import static com.skedgo.android.tripkit.BookingResolver.FLITWAYS;
import static com.skedgo.android.tripkit.BookingResolver.GOCATCH;
import static com.skedgo.android.tripkit.BookingResolver.INGOGO;
import static com.skedgo.android.tripkit.BookingResolver.LYFT;
import static com.skedgo.android.tripkit.BookingResolver.OTHERS;
import static com.skedgo.android.tripkit.BookingResolver.SMS;
import static com.skedgo.android.tripkit.BookingResolver.UBER;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class BookingResolverImplTest {
  @Mock PackageManager packageManager;
  @Mock Func1<ReverseGeocodingParams, Observable<String>> reverseGeocoderFactory;
  private BookingResolverImpl bookingResolver;

  @Before public void before() {
    MockitoAnnotations.initMocks(this);
    bookingResolver = new BookingResolverImpl(
        RuntimeEnvironment.application.getResources(),
        packageManager,
        reverseGeocoderFactory
    );
  }

  @Test public void hasUberApp() throws PackageManager.NameNotFoundException {
    when(packageManager.getPackageInfo(
        eq("com.ubercab"),
        eq(PackageManager.GET_ACTIVITIES)
    )).thenReturn(new PackageInfo());

    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("uber")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(UBER)
        .hasApp(true)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse("uber://")))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void hasNoUberApp() throws PackageManager.NameNotFoundException {
    when(packageManager.getPackageInfo(
        eq("com.ubercab"),
        eq(PackageManager.GET_ACTIVITIES)
    )).thenThrow(new PackageManager.NameNotFoundException());

    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("uber")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(UBER)
        .hasApp(false)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse("https://m.uber.com/sign-up")))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void hasLyftApp() throws PackageManager.NameNotFoundException {
    when(packageManager.getPackageInfo(
        eq("me.lyft.android"),
        eq(PackageManager.GET_ACTIVITIES)
    )).thenReturn(new PackageInfo());

    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("lyft")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(LYFT)
        .hasApp(true)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse("lyft://")))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void hasNoLyftApp() throws PackageManager.NameNotFoundException {
    when(packageManager.getPackageInfo(
        eq("me.lyft.android"),
        eq(PackageManager.GET_ACTIVITIES)
    )).thenThrow(new PackageManager.NameNotFoundException());

    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("lyft")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final Intent data = new Intent(Intent.ACTION_VIEW)
        .setData(Uri.parse("https://play.google.com/store/apps/details?id=me.lyft.android"));
    final BookingAction action = BookingAction.builder()
        .bookingProvider(LYFT)
        .hasApp(false)
        .data(data)
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void hasNoFlitwaysApp_noPartnerKey() {
    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("flitways")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(FLITWAYS)
        .hasApp(false)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse("https://flitways.com")))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void hasNoFlitwaysApp_hasPartnerKey() {
    when(reverseGeocoderFactory.call(any(ReverseGeocodingParams.class)))
        .thenAnswer(new Answer<Observable<String>>() {
          @Override public Observable<String> answer(InvocationOnMock invocation) {
            final ReverseGeocodingParams params = invocation.getArgumentAt(0, ReverseGeocodingParams.class);
            if (params.lat() == 1.0) {
              return Observable.just("A");
            } else {
              return Observable.just("B");
            }
          }
        });

    final Calendar time = Calendar.getInstance(TimeZone.getTimeZone("Australia/Sydney"), Locale.US);
    time.set(2014, Calendar.FEBRUARY, 14, 11, 10);

    final TripSegment segment = mock(TripSegment.class);
    when(segment.getFrom()).thenReturn(new Location(1.0, 2.0));
    when(segment.getTo()).thenReturn(new Location(3.0, 4.0));
    when(segment.getTimeZone()).thenReturn("Australia/Sydney");
    when(segment.getStartTimeInSecs()).thenReturn(TimeUnit.MILLISECONDS.toSeconds(time.getTimeInMillis()));

    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("flitways")
            .segment(segment)
            .flitWaysPartnerKey("25251325")
            .build()
    ).subscribe(subscriber);

    final String url = "https://flitways.com/api/link?trip_date=02/14/2014%2011:10%20AM&key=25251325&pickup=A&destination=B";
    final BookingAction action = BookingAction.builder()
        .bookingProvider(FLITWAYS)
        .hasApp(false)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void hasNoApp() {
    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("https://github.com/")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(OTHERS)
        .hasApp(false)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/")))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void hasNoGoCatchApp() throws PackageManager.NameNotFoundException {
    when(packageManager.getPackageInfo(
        eq("com.gocatchapp.goCatch"),
        eq(PackageManager.GET_ACTIVITIES)
    )).thenThrow(new PackageManager.NameNotFoundException());

    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("gocatch")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final Intent data = new Intent(Intent.ACTION_VIEW)
        .setData(Uri.parse("https://play.google.com/store/apps/details?id=com.gocatchapp.goCatch"));
    final BookingAction action = BookingAction.builder()
        .bookingProvider(GOCATCH)
        .hasApp(false)
        .data(data)
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void hasGoCatchApp() throws PackageManager.NameNotFoundException {
    when(reverseGeocoderFactory.call(any(ReverseGeocodingParams.class)))
        .thenAnswer(new Answer<Observable<String>>() {
          @Override public Observable<String> answer(InvocationOnMock invocation) {
            final ReverseGeocodingParams params = invocation.getArgumentAt(0, ReverseGeocodingParams.class);
            if (params.lat() == 1.0) {
              return Observable.just("A");
            } else {
              return Observable.just("B");
            }
          }
        });

    when(packageManager.getPackageInfo(
        eq("com.gocatchapp.goCatch"),
        eq(PackageManager.GET_ACTIVITIES)
    )).thenReturn(new PackageInfo());

    final TripSegment segment = mock(TripSegment.class);
    when(segment.getFrom()).thenReturn(new Location(1.0, 2.0));
    when(segment.getTo()).thenReturn(new Location(3.0, 4.0));
    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("gocatch")
            .segment(segment)
            .build()
    ).subscribe(subscriber);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(GOCATCH)
        .hasApp(true)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse("gocatch://referral?code=tripgo&destination=B&pickup=&lat=1.0&lng=2.0")))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void hasIngogoApp() throws PackageManager.NameNotFoundException {
    when(packageManager.getPackageInfo(
        eq("com.ingogo.passenger"),
        eq(PackageManager.GET_ACTIVITIES)
    )).thenReturn(new PackageInfo());

    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("ingogo")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(INGOGO)
        .hasApp(true)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse("ingogo://")))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void hasNoIngogoApp() throws PackageManager.NameNotFoundException {
    when(packageManager.getPackageInfo(
        eq("com.ingogo.passenger"),
        eq(PackageManager.GET_ACTIVITIES)
    )).thenThrow(new PackageManager.NameNotFoundException());

    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("ingogo")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(INGOGO)
        .hasApp(false)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.ingogo.passenger")))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void handleSMS() {
    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("sms:12345")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    String body = null;
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:12345"));
    intent.putExtra("sms_body", body).putExtra(Intent.EXTRA_TEXT, body);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(SMS)
        .hasApp(false)
        .data(intent)
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void handleSMSWithBody() {
    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("sms:12345?Body goes here")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    String body = "Body goes here";
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:12345"));
    intent.putExtra("sms_body", body).putExtra(Intent.EXTRA_TEXT, body);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(SMS)
        .hasApp(false)
        .data(intent)
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void handleTel() {
    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("tel:12345")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(OTHERS)
        .hasApp(false)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse("tel:12345")))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void handleTelWithName() {
    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("tel:12345?name=user")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);

    final BookingAction action = BookingAction.builder()
        .bookingProvider(OTHERS)
        .hasApp(false)
        .data(new Intent(Intent.ACTION_VIEW, Uri.parse("tel:12345")))
        .build();
    subscriber.awaitTerminalEvent();
    subscriber.assertNoErrors();
    subscriber.assertValue(action);
  }

  @Test public void strangeExternalAction() {
    final TestSubscriber<BookingAction> subscriber = new TestSubscriber<>();
    bookingResolver.performExternalActionAsync(
        ExternalActionParams.builder()
            .action("Some strange action")
            .segment(mock(TripSegment.class))
            .build()
    ).subscribe(subscriber);
    subscriber.assertError(UnsupportedOperationException.class);
  }
}