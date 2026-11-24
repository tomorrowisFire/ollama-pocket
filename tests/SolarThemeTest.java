import com.ollamapocket.app.SolarTheme;
import java.time.*;

public final class SolarThemeTest {
    static void verify(boolean value,String name){if(!value)throw new AssertionError(name);}
    static boolean night(String stamp,String zone){
        return SolarTheme.isNight(Instant.parse(stamp),ZoneId.of(zone));
    }
    public static void main(String[] args){
        ZoneId seoul=ZoneId.of("Asia/Seoul");
        double[] winter=SolarTheme.daylightMinutes(LocalDate.of(2026,12,21),seoul,37.5665,126.978);
        double[] summer=SolarTheme.daylightMinutes(LocalDate.of(2026,6,21),seoul,37.5665,126.978);
        verify(winter[0]>420&&winter[0]<500,"Seoul winter sunrise local morning");
        verify(winter[1]>990&&winter[1]<1070,"Seoul winter sunset local afternoon");
        verify(summer[0]>270&&summer[0]<360,"Seoul summer sunrise early");
        verify(summer[1]>1130&&summer[1]<1240,"Seoul summer sunset late");
        verify(night("2026-12-21T08:00:00Z","Asia/Seoul"),"winter sunset: 17:00KST");
        verify(!night("2026-12-21T03:00:00Z","Asia/Seoul"),"winter noon light");
        verify(night("2026-06-21T11:00:00Z","Asia/Seoul"),"summer night at 20:00");
        verify(!night("2026-06-21T07:00:00Z","Asia/Seoul"),"summer afternoon light");
        verify(!SolarTheme.isNight(Instant.parse("2026-06-21T09:00:00Z"),
            ZoneId.of("Europe/London")),"London summer daytime");
        verify(SolarTheme.approximateCoordinates(seoul)[0]>37,"Korean timezone");
        System.out.println("PASS: SolarTheme day/night + seasons and zones (10 checks)");
    }
}
