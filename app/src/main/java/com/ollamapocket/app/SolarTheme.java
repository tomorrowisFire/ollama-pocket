package com.ollamapocket.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Offline approximate sunrise/sunset; no location permission, network or background tracking. */
public final class SolarTheme {
    private SolarTheme(){}

    /** Geographic center/representative city of the device time zone, not the user's exact position. */
    public static double[] approximateCoordinates(ZoneId zone){
        String name=zone.getId();
        switch(name){
            case "Asia/Seoul":return new double[]{37.5665,126.9780};
            case "Asia/Pyongyang":return new double[]{39.0392,125.7625};
            case "Asia/Tokyo":return new double[]{35.6762,139.6503};
            case "Asia/Shanghai":return new double[]{31.2304,121.4737};
            case "Asia/Hong_Kong":return new double[]{22.3193,114.1694};
            case "Asia/Taipei":return new double[]{25.0330,121.5654};
            case "Asia/Singapore":return new double[]{1.3521,103.8198};
            case "Asia/Bangkok":return new double[]{13.7563,100.5018};
            case "Asia/Kolkata":return new double[]{22.5726,88.3639};
            case "Australia/Sydney":return new double[]{-33.8688,151.2093};
            case "Europe/London":return new double[]{51.5072,-0.1276};
            case "Europe/Paris":return new double[]{48.8566,2.3522};
            case "Europe/Berlin":return new double[]{52.5200,13.4050};
            case "America/New_York":return new double[]{40.7128,-74.0060};
            case "America/Chicago":return new double[]{41.8781,-87.6298};
            case "America/Denver":return new double[]{39.7392,-104.9903};
            case "America/Los_Angeles":return new double[]{34.0522,-118.2437};
            case "America/Toronto":return new double[]{43.6532,-79.3832};
            case "Pacific/Honolulu":return new double[]{21.3099,-157.8581};
            default:
                // For unmapped zones use longitude from UTC offset as an approximation.
                double longitude=zone.getRules().getOffset(Instant.now())
                    .getTotalSeconds()/3600.0*15.0;
                return new double[]{35.0,longitude};
        }
    }

    /** Local sunrise/sunset in minutes since midnight. Approximate accuracy: several minutes. */
    public static double[] daylightMinutes(LocalDate day,ZoneId zone,double latitude,double longitude){
        double gamma=2*Math.PI*(day.getDayOfYear()-1)/(day.isLeapYear()?366:365);
        double eqtime=229.18*(0.000075+0.001868*Math.cos(gamma)-
            0.032077*Math.sin(gamma)-0.014615*Math.cos(2*gamma)-
            0.040849*Math.sin(2*gamma));
        double decl=0.006918-0.399912*Math.cos(gamma)+0.070257*Math.sin(gamma)-
            0.006758*Math.cos(2*gamma)+0.000907*Math.sin(2*gamma)-
            0.002697*Math.cos(3*gamma)+0.00148*Math.sin(3*gamma);
        double lat=Math.toRadians(Math.max(-89.0,Math.min(89.0,latitude)));
        double cosHour=(Math.cos(Math.toRadians(90.833))/
            (Math.cos(lat)*Math.cos(decl)))-Math.tan(lat)*Math.tan(decl);
        if(cosHour>1)return new double[]{1440,1440}; // polar night
        if(cosHour< -1)return new double[]{0,1440}; // midnight sun
        double hourAngle=Math.toDegrees(Math.acos(cosHour));
        int offset=ZonedDateTime.of(day,LocalTime.NOON,zone).getOffset().getTotalSeconds()/60;
        double solarNoon=720-4*longitude-eqtime+offset;
        return new double[]{solarNoon-4*hourAngle,solarNoon+4*hourAngle};
    }

    public static boolean isNight(Instant now,ZoneId zone){
        ZonedDateTime local=now.atZone(zone);
        double[] coords=approximateCoordinates(zone);
        double[] light=daylightMinutes(local.toLocalDate(),zone,coords[0],coords[1]);
        double minute=local.getHour()*60+local.getMinute()+local.getSecond()/60.0;
        return minute<light[0]||minute>=light[1];
    }
}
