package com.droid.dolphy.hid

object UsbHidKeyCodes {
    // Modifier masks (1 byte bitfield)
    const val MOD_NONE: Byte = 0x00
    const val MOD_LCTRL: Byte = 0x01
    const val MOD_LSHIFT: Byte = 0x02
    const val MOD_LALT: Byte = 0x04
    const val MOD_LMETA: Byte = 0x08 // Windows / Super / GUI key
    const val MOD_RCTRL: Byte = 0x10
    const val MOD_RSHIFT: Byte = 0x20
    const val MOD_RALT: Byte = 0x40
    const val MOD_RMETA: Byte = 0x80.toByte()

    // Keycodes (Standard USB HID Usage Tables v1.12 Section 10)
    const val KEY_NONE: Byte = 0x00

    // Letters
    const val KEY_A: Byte = 0x04
    const val KEY_B: Byte = 0x05
    const val KEY_C: Byte = 0x06
    const val KEY_D: Byte = 0x07
    const val KEY_E: Byte = 0x08
    const val KEY_F: Byte = 0x09
    const val KEY_G: Byte = 0x0A
    const val KEY_H: Byte = 0x0B
    const val KEY_I: Byte = 0x0C
    const val KEY_J: Byte = 0x0D
    const val KEY_K: Byte = 0x0E
    const val KEY_L: Byte = 0x0F
    const val KEY_M: Byte = 0x10
    const val KEY_N: Byte = 0x11
    const val KEY_O: Byte = 0x12
    const val KEY_P: Byte = 0x13
    const val KEY_Q: Byte = 0x14
    const val KEY_R: Byte = 0x15
    const val KEY_S: Byte = 0x16
    const val KEY_T: Byte = 0x17
    const val KEY_U: Byte = 0x18
    const val KEY_V: Byte = 0x19
    const val KEY_W: Byte = 0x1A
    const val KEY_X: Byte = 0x1B
    const val KEY_Y: Byte = 0x1C
    const val KEY_Z: Byte = 0x1D

    // Numbers
    const val KEY_1: Byte = 0x1E
    const val KEY_2: Byte = 0x1F
    const val KEY_3: Byte = 0x20
    const val KEY_4: Byte = 0x21
    const val KEY_5: Byte = 0x22
    const val KEY_6: Byte = 0x23
    const val KEY_7: Byte = 0x24
    const val KEY_8: Byte = 0x25
    const val KEY_9: Byte = 0x26
    const val KEY_0: Byte = 0x27

    // Control & Editing
    const val KEY_ENTER: Byte = 0x28
    const val KEY_ESC: Byte = 0x29
    const val KEY_BACKSPACE: Byte = 0x2A
    const val KEY_TAB: Byte = 0x2B
    const val KEY_SPACE: Byte = 0x2C

    // Symbols
    const val KEY_MINUS: Byte = 0x2D       // - and _
    const val KEY_EQUAL: Byte = 0x2E       // = and +
    const val KEY_LEFTBRACE: Byte = 0x2F   // [ and {
    const val KEY_RIGHTBRACE: Byte = 0x30  // ] and }
    const val KEY_BACKSLASH: Byte = 0x31   // \ and |
    const val KEY_SEMICOLON: Byte = 0x33   // ; and :
    const val KEY_APOSTROPHE: Byte = 0x34  // ' and "
    const val KEY_GRAVE: Byte = 0x35       // ` and ~
    const val KEY_COMMA: Byte = 0x36       // , and <
    const val KEY_DOT: Byte = 0x37         // . and >
    const val KEY_SLASH: Byte = 0x38       // / and ?

    const val KEY_CAPSLOCK: Byte = 0x39

    // Function row
    const val KEY_F1: Byte = 0x3A
    const val KEY_F2: Byte = 0x3B
    const val KEY_F3: Byte = 0x3C
    const val KEY_F4: Byte = 0x3D
    const val KEY_F5: Byte = 0x3E
    const val KEY_F6: Byte = 0x3F
    const val KEY_F7: Byte = 0x40
    const val KEY_F8: Byte = 0x41
    const val KEY_F9: Byte = 0x42
    const val KEY_F10: Byte = 0x43
    const val KEY_F11: Byte = 0x44
    const val KEY_F12: Byte = 0x45

    // System & Navigation
    const val KEY_SYSRQ: Byte = 0x46 // PrintScreen / SysRq
    const val KEY_SCROLLLOCK: Byte = 0x47
    const val KEY_PAUSE: Byte = 0x48
    const val KEY_INSERT: Byte = 0x49
    const val KEY_HOME: Byte = 0x4A
    const val KEY_PAGEUP: Byte = 0x4B
    const val KEY_DELETE: Byte = 0x4C
    const val KEY_END: Byte = 0x4D
    const val KEY_PAGEDOWN: Byte = 0x4E
    const val KEY_RIGHT: Byte = 0x4F
    const val KEY_LEFT: Byte = 0x50
    const val KEY_DOWN: Byte = 0x51
    const val KEY_UP: Byte = 0x52

    // Keypad / NumPad
    const val KEY_NUMLOCK: Byte = 0x53
    const val KEY_KPSLASH: Byte = 0x54
    const val KEY_KPASTERISK: Byte = 0x55
    const val KEY_KPMINUS: Byte = 0x56
    const val KEY_KPPLUS: Byte = 0x57
    const val KEY_KPENTER: Byte = 0x58
    const val KEY_KP1: Byte = 0x59
    const val KEY_KP2: Byte = 0x5A
    const val KEY_KP3: Byte = 0x5B
    const val KEY_KP4: Byte = 0x5C
    const val KEY_KP5: Byte = 0x5D
    const val KEY_KP6: Byte = 0x5E
    const val KEY_KP7: Byte = 0x5F
    const val KEY_KP8: Byte = 0x60
    const val KEY_KP9: Byte = 0x61
    const val KEY_KP0: Byte = 0x62
    const val KEY_KPDOT: Byte = 0x63

    // Application / Context Menu
    const val KEY_APPLICATION: Byte = 0x65

    fun charToHid(c: Char, isRussianLayout: Boolean = false): Pair<Byte, Byte>? {
        if (isRussianLayout) {
            when (c) {
                // Russian layout punctuation mappings (Standard PC Russian keyboard)
                '.' -> return Pair(MOD_NONE, KEY_SLASH)
                ',' -> return Pair(MOD_LSHIFT, KEY_SLASH)
                ';' -> return Pair(MOD_LSHIFT, KEY_4)
                ':' -> return Pair(MOD_LSHIFT, KEY_6)
                '?' -> return Pair(MOD_LSHIFT, KEY_7)
                '!' -> return Pair(MOD_LSHIFT, KEY_1)
                '"', '«', '»', '“', '”', '„' -> return Pair(MOD_LSHIFT, KEY_2)
                '№' -> return Pair(MOD_LSHIFT, KEY_3)
                '%' -> return Pair(MOD_LSHIFT, KEY_5)
                '*' -> return Pair(MOD_LSHIFT, KEY_8)
                '(' -> return Pair(MOD_LSHIFT, KEY_9)
                ')' -> return Pair(MOD_LSHIFT, KEY_0)
                '-', '—', '–' -> return Pair(MOD_NONE, KEY_MINUS)
                '_' -> return Pair(MOD_LSHIFT, KEY_MINUS)
                '=' -> return Pair(MOD_NONE, KEY_EQUAL)
                '+' -> return Pair(MOD_LSHIFT, KEY_EQUAL)
                '/' -> return Pair(MOD_LSHIFT, KEY_BACKSLASH)
                '\\' -> return Pair(MOD_NONE, KEY_BACKSLASH)
            }
        }

        return when (c) {
            in 'a'..'z' -> Pair(MOD_NONE, (KEY_A + (c - 'a')).toByte())
            in 'A'..'Z' -> Pair(MOD_LSHIFT, (KEY_A + (c - 'A')).toByte())
            '1' -> Pair(MOD_NONE, KEY_1)
            '2' -> Pair(MOD_NONE, KEY_2)
            '3' -> Pair(MOD_NONE, KEY_3)
            '4' -> Pair(MOD_NONE, KEY_4)
            '5' -> Pair(MOD_NONE, KEY_5)
            '6' -> Pair(MOD_NONE, KEY_6)
            '7' -> Pair(MOD_NONE, KEY_7)
            '8' -> Pair(MOD_NONE, KEY_8)
            '9' -> Pair(MOD_NONE, KEY_9)
            '0' -> Pair(MOD_NONE, KEY_0)
            '!' -> Pair(MOD_LSHIFT, KEY_1)
            '@' -> Pair(MOD_LSHIFT, KEY_2)
            '#' -> Pair(MOD_LSHIFT, KEY_3)
            '$' -> Pair(MOD_LSHIFT, KEY_4)
            '%' -> Pair(MOD_LSHIFT, KEY_5)
            '^' -> Pair(MOD_LSHIFT, KEY_6)
            '&' -> Pair(MOD_LSHIFT, KEY_7)
            '*' -> Pair(MOD_LSHIFT, KEY_8)
            '(' -> Pair(MOD_LSHIFT, KEY_9)
            ')' -> Pair(MOD_LSHIFT, KEY_0)
            ' ' -> Pair(MOD_NONE, KEY_SPACE)
            '\n' -> Pair(MOD_NONE, KEY_ENTER)
            '\t' -> Pair(MOD_NONE, KEY_TAB)
            '-' -> Pair(MOD_NONE, KEY_MINUS)
            '_' -> Pair(MOD_LSHIFT, KEY_MINUS)
            '=' -> Pair(MOD_NONE, KEY_EQUAL)
            '+' -> Pair(MOD_LSHIFT, KEY_EQUAL)
            '[' -> Pair(MOD_NONE, KEY_LEFTBRACE)
            '{' -> Pair(MOD_LSHIFT, KEY_LEFTBRACE)
            ']' -> Pair(MOD_NONE, KEY_RIGHTBRACE)
            '}' -> Pair(MOD_LSHIFT, KEY_RIGHTBRACE)
            '\\' -> Pair(MOD_NONE, KEY_BACKSLASH)
            '|' -> Pair(MOD_LSHIFT, KEY_BACKSLASH)
            ';' -> Pair(MOD_NONE, KEY_SEMICOLON)
            ':' -> Pair(MOD_LSHIFT, KEY_SEMICOLON)
            '\'' -> Pair(MOD_NONE, KEY_APOSTROPHE)
            '"' -> Pair(MOD_LSHIFT, KEY_APOSTROPHE)
            '`' -> Pair(MOD_NONE, KEY_GRAVE)
            '~' -> Pair(MOD_LSHIFT, KEY_GRAVE)
            ',' -> Pair(MOD_NONE, KEY_COMMA)
            '<' -> Pair(MOD_LSHIFT, KEY_COMMA)
            '.' -> Pair(MOD_NONE, KEY_DOT)
            '>' -> Pair(MOD_LSHIFT, KEY_DOT)
            '/' -> Pair(MOD_NONE, KEY_SLASH)
            '?' -> Pair(MOD_LSHIFT, KEY_SLASH)

            // Russian Cyrillic physical key mapping (ЙЦУКЕН scan code positions)
            'й' -> Pair(MOD_NONE, KEY_Q)
            'Й' -> Pair(MOD_LSHIFT, KEY_Q)
            'ц' -> Pair(MOD_NONE, KEY_W)
            'Ц' -> Pair(MOD_LSHIFT, KEY_W)
            'у' -> Pair(MOD_NONE, KEY_E)
            'У' -> Pair(MOD_LSHIFT, KEY_E)
            'к' -> Pair(MOD_NONE, KEY_R)
            'К' -> Pair(MOD_LSHIFT, KEY_R)
            'е' -> Pair(MOD_NONE, KEY_T)
            'Е' -> Pair(MOD_LSHIFT, KEY_T)
            'н' -> Pair(MOD_NONE, KEY_Y)
            'Н' -> Pair(MOD_LSHIFT, KEY_Y)
            'г' -> Pair(MOD_NONE, KEY_U)
            'Г' -> Pair(MOD_LSHIFT, KEY_U)
            'ш' -> Pair(MOD_NONE, KEY_I)
            'Ш' -> Pair(MOD_LSHIFT, KEY_I)
            'щ' -> Pair(MOD_NONE, KEY_O)
            'Щ' -> Pair(MOD_LSHIFT, KEY_O)
            'з' -> Pair(MOD_NONE, KEY_P)
            'З' -> Pair(MOD_LSHIFT, KEY_P)
            'х' -> Pair(MOD_NONE, KEY_LEFTBRACE)
            'Х' -> Pair(MOD_LSHIFT, KEY_LEFTBRACE)
            'ъ' -> Pair(MOD_NONE, KEY_RIGHTBRACE)
            'Ъ' -> Pair(MOD_LSHIFT, KEY_RIGHTBRACE)
            'ф' -> Pair(MOD_NONE, KEY_A)
            'Ф' -> Pair(MOD_LSHIFT, KEY_A)
            'ы' -> Pair(MOD_NONE, KEY_S)
            'Ы' -> Pair(MOD_LSHIFT, KEY_S)
            'в' -> Pair(MOD_NONE, KEY_D)
            'В' -> Pair(MOD_LSHIFT, KEY_D)
            'а' -> Pair(MOD_NONE, KEY_F)
            'А' -> Pair(MOD_LSHIFT, KEY_F)
            'п' -> Pair(MOD_NONE, KEY_G)
            'П' -> Pair(MOD_LSHIFT, KEY_G)
            'р' -> Pair(MOD_NONE, KEY_H)
            'Р' -> Pair(MOD_LSHIFT, KEY_H)
            'о' -> Pair(MOD_NONE, KEY_J)
            'О' -> Pair(MOD_LSHIFT, KEY_J)
            'л' -> Pair(MOD_NONE, KEY_K)
            'Л' -> Pair(MOD_LSHIFT, KEY_K)
            'д' -> Pair(MOD_NONE, KEY_L)
            'Д' -> Pair(MOD_LSHIFT, KEY_L)
            'ж' -> Pair(MOD_NONE, KEY_SEMICOLON)
            'Ж' -> Pair(MOD_LSHIFT, KEY_SEMICOLON)
            'э' -> Pair(MOD_NONE, KEY_APOSTROPHE)
            'Э' -> Pair(MOD_LSHIFT, KEY_APOSTROPHE)
            'я' -> Pair(MOD_NONE, KEY_Z)
            'Я' -> Pair(MOD_LSHIFT, KEY_Z)
            'ч' -> Pair(MOD_NONE, KEY_X)
            'Ч' -> Pair(MOD_LSHIFT, KEY_X)
            'с' -> Pair(MOD_NONE, KEY_C)
            'С' -> Pair(MOD_LSHIFT, KEY_C)
            'м' -> Pair(MOD_NONE, KEY_V)
            'М' -> Pair(MOD_LSHIFT, KEY_V)
            'и' -> Pair(MOD_NONE, KEY_B)
            'И' -> Pair(MOD_LSHIFT, KEY_B)
            'т' -> Pair(MOD_NONE, KEY_N)
            'Т' -> Pair(MOD_LSHIFT, KEY_N)
            'ь' -> Pair(MOD_NONE, KEY_M)
            'Ь' -> Pair(MOD_LSHIFT, KEY_M)
            'б' -> Pair(MOD_NONE, KEY_COMMA)
            'Б' -> Pair(MOD_LSHIFT, KEY_COMMA)
            'ю' -> Pair(MOD_NONE, KEY_DOT)
            'Ю' -> Pair(MOD_LSHIFT, KEY_DOT)
            'ё' -> Pair(MOD_NONE, KEY_GRAVE)
            'Ё' -> Pair(MOD_LSHIFT, KEY_GRAVE)
            '№' -> Pair(MOD_LSHIFT, KEY_3)

            // Mobile typographic punctuation & whitespace normalization
            '—', '–' -> Pair(MOD_NONE, KEY_MINUS)
            '«', '»', '“', '”', '„' -> Pair(MOD_LSHIFT, KEY_APOSTROPHE)
            '’', '‘' -> Pair(MOD_NONE, KEY_APOSTROPHE)
            '…' -> if (isRussianLayout) Pair(MOD_NONE, KEY_SLASH) else Pair(MOD_NONE, KEY_DOT)
            '\u00A0' -> Pair(MOD_NONE, KEY_SPACE)
            '\r' -> Pair(MOD_NONE, KEY_ENTER)

            else -> null
        }
    }
}
