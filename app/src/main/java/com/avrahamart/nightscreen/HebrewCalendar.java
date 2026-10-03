package com.avrahamart.nightscreen;

public final class HebrewCalendar {
    private static final String[] MONTH_COMMON = {
        "ניסן", "אייר", "סיוון", "תמוז", "אב", "אלול",
        "תשרי", "חשוון", "כסלו", "טבת", "שבט", "אדר"
    };
    private static final String[] MONTH_LEAP = {
        "ניסן", "אייר", "סיוון", "תמוז", "אב", "אלול",
        "תשרי", "חשוון", "כסלו", "טבת", "שבט", "אדר א׳", "אדר ב׳"
    };

    private HebrewCalendar() {}

    public static String formatToday(java.util.Calendar greg) {
        int gy = greg.get(java.util.Calendar.YEAR);
        int gm = greg.get(java.util.Calendar.MONTH) + 1;
        int gd = greg.get(java.util.Calendar.DAY_OF_MONTH);

        long jd = gregorianToJd(gy, gm, gd);
        int hy = hebrewYearAt(jd);
        long rosh = hebrewToJd(hy, 7, 1);
        int elapsed = (int) (jd - rosh);

        int hm = 7;
        while (hm <= (isLeap(hy) ? 13 : 12)) {
            int len = daysInHebrewMonth(hy, hm);
            if (elapsed < len) break;
            elapsed -= len;
            hm = hm == (isLeap(hy) ? 13 : 12) ? 1 : hm + 1;
        }

        int hd = elapsed + 1;
        String[] months = isLeap(hy) ? MONTH_LEAP : MONTH_COMMON;
        return dayName(hd) + " " + months[hm - 1] + " " + hebrewYearName(hy);
    }

    private static int hebrewYearAt(long jd) {
        int year = (int) (Math.floor((jd - 347997) / 365.2468) + 3760);
        while (hebrewToJd(year + 1, 7, 1) <= jd) year++;
        while (hebrewToJd(year, 7, 1) > jd) year--;
        return year;
    }

    private static long hebrewToJd(int year, int month, int day) {
        long jd = hebrewDelay1(year) + hebrewDelay2(year) + day + 347997L;

        if (month < 7) {
            int months = isLeap(year) ? 13 : 12;
            for (int m = 7; m <= months; m++) {
                jd += daysInHebrewMonth(year, m);
            }
            for (int m = 1; m < month; m++) {
                jd += daysInHebrewMonth(year, m);
            }
        } else {
            for (int m = 7; m < month; m++) {
                jd += daysInHebrewMonth(year, m);
            }
        }
        return jd;
    }

    private static long hebrewDelay1(int year) {
        long months = (235L * year - 234) / 19;
        long parts = 12084L + 13753L * months;
        long day = months * 29L + parts / 25920L;
        if ((3 * (day + 1)) % 7 < 3) day++;
        return day;
    }

    private static long hebrewDelay2(int year) {
        long last = hebrewDelay1(year - 1);
        long present = hebrewDelay1(year);
        long next = hebrewDelay1(year + 1);
        if (next - present == 356) return 2;
        if (present - last == 382) return 1;
        return 0;
    }

    private static int daysInHebrewYear(int year) {
        return (int) ((hebrewDelay1(year + 1) + hebrewDelay2(year + 1))
                - (hebrewDelay1(year) + hebrewDelay2(year)));
    }

    private static int daysInHebrewMonth(int year, int month) {
        if (month == 2 || month == 4 || month == 6 || month == 10 || month == 13) return 29;
        if (month == 12 && !isLeap(year)) return 29;
        if (month == 8 && !longHeshvan(year)) return 29;
        if (month == 9 && shortKislev(year)) return 29;
        return 30;
    }

    private static boolean longHeshvan(int year) {
        return daysInHebrewYear(year) % 10 == 5;
    }

    private static boolean shortKislev(int year) {
        return daysInHebrewYear(year) % 10 == 3;
    }

    private static boolean isLeap(int year) {
        return ((7 * year + 1) % 19) < 7;
    }

    private static long gregorianToJd(int year, int month, int day) {
        int a = (14 - month) / 12;
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        return day + (153L * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045L;
    }

    private static String dayName(int day) {
        return hebrewNumber(day);
    }

    private static String hebrewYearName(int year) {
        int y = year % 1000;
        return "ה׳" + hebrewNumber(y);
    }

    private static String hebrewNumber(int n) {
        final String[] hundreds = {"", "ק", "ר", "ש", "ת"};
        final String[] tens = {"", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ"};
        final String[] ones = {"", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט"};

        if (n <= 0) return "";
        if (n == 15) return "ט״ו";
        if (n == 16) return "ט״ז";

        StringBuilder out = new StringBuilder();
        int h = n / 100;
        int t = (n % 100) / 10;
        int o = n % 10;

        while (h >= 4) {
            out.append("ת");
            h -= 4;
        }
        if (h > 0) out.append(hundreds[h]);
        if (t > 0) out.append(tens[t]);
        if (o > 0) out.append(ones[o]);

        String s = out.toString();
        if (s.length() == 1) return s + "׳";
        return s.substring(0, s.length() - 1) + "״" + s.substring(s.length() - 1);
    }
}
