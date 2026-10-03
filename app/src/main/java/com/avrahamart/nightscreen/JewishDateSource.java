package com.avrahamart.nightscreen;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.time.LocalDate;

public final class JewishDateSource {
    private static final HebrewDateFormatter FORMATTER = createFormatter();

    private JewishDateSource() {}

    private static HebrewDateFormatter createFormatter() {
        HebrewDateFormatter formatter = new HebrewDateFormatter();
        formatter.setHebrewFormat(true);
        formatter.setUseLongHebrewYears(true);
        return formatter;
    }

    public static String today() {
        JewishDate date = new JewishDate(LocalDate.now());
        return FORMATTER.format(date);
    }
}
