package ir.bridge.maintenance;

import static org.junit.Assert.*;
import org.junit.Test;

public class MediaNamingTest {
 @Test public void compactDateNormalizesPersianAndArabicDigits(){assertEquals("14050520",MediaNaming.compactDate("۱۴۰۵/۰۵/۲۰"));assertEquals("14050520",MediaNaming.compactDate("١٤٠٥/٠٥/٢٠"));}
 @Test public void safeNameRemovesPathCharacters(){String s=MediaNaming.safeName("پل آزادی/../../گزارش:۱");assertFalse(s.contains("/"));assertFalse(s.contains(":"));}
 @Test public void legacyIdentityIsDeterministic() throws Exception {assertEquals(MediaNaming.legacyMediaId("inspection","x","$.photo","abc"),MediaNaming.legacyMediaId("inspection","x","$.photo","abc"));}
 @Test public void safeRelativePathIsPreserved(){assertEquals("Images/14050520/a.jpg",PathSecurity.sanitizeRelative("Images/14050520/a.jpg"));}
 @Test(expected=SecurityException.class) public void zipSlipIsRejected(){PathSecurity.sanitizeRelative("../outside.jpg");}
 @Test(expected=SecurityException.class) public void absolutePathIsRejected(){PathSecurity.sanitizeRelative("/outside.jpg");}
}
