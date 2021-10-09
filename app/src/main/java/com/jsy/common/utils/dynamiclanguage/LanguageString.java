/**
 * Secret
 * Copyright (C) 2021 Secret
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jsy.common.utils.dynamiclanguage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class LanguageString {

  private LanguageString() {
  }

  /**
   * @param languageString String in format language_REGION, e.g. en_US
   * @return Locale, or null if cannot parse
   */
  @Nullable
  public static Locale parseLocale(@Nullable String languageString) {
    if (languageString == null || languageString.isEmpty()) {
      return null;
    }

    final Locale locale = createLocale(languageString);

    if (!isValid(locale)) {
      return null;
    } else {
      return locale;
    }
  }

  private static Locale createLocale(@NonNull String languageString) {
    final String[] language = languageString.split("_");
    if (language.length >= 2) {
      return new Locale(language[0], language[1]);
    } else {
      return new Locale(language[0]);
    }
  }

  private static boolean isValid(@NonNull Locale locale) {
    try {
      return locale.getISO3Language() != null && locale.getISO3Country() != null;
    } catch (Exception ex) {
      return false;
    }
  }
}
