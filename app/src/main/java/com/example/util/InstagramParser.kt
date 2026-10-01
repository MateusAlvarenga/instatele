package com.example.util

object InstagramParser {

    /**
     * Extracts original Instagram username from file name.
     * Example:
     * "eumariaemanuellyoficial_3944008708912508440.mp4" -> "eumariaemanuellyoficial"
     * "@user.name_20231201_UTC.jpg" -> "user.name"
     * "profile_name-something.mp4" -> "profile_name"
     */
    fun extractUsername(fileName: String): String {
        val baseName = fileName.substringBeforeLast('.').trim()
        val noAt = baseName.removePrefix("@")

        // Strip trailing hyphen-numeric IDs like profile_photo-123456
        val cleanHyphen = if (noAt.contains('-')) {
            val suffix = noAt.substringAfterLast('-')
            if (suffix.matches(Regex("""^\d+$"""))) {
                noAt.substringBeforeLast('-')
            } else {
                noAt
            }
        } else {
            noAt
        }

        // Split by underscore to extract username before media ID or timestamp
        val parts = cleanHyphen.split('_')
        if (parts.size > 1) {
            val usernameParts = mutableListOf<String>()
            for (part in parts) {
                val isMeta = part.equals("UTC", ignoreCase = true) ||
                        part.matches(Regex("""^\d{4,}$""")) ||
                        part.matches(Regex("""^\d{4}-\d{2}-\d{2}.*$"""))
                if (isMeta && usernameParts.isNotEmpty()) {
                    break
                }
                usernameParts.add(part)
            }
            val candidate = usernameParts.joinToString("_")
            if (candidate.isNotEmpty() && Regex("^[a-zA-Z0-9._]{1,30}$").matches(candidate)) {
                return candidate
            }
        }

        // Single part fallback
        if (Regex("^[a-zA-Z0-9._]{1,30}$").matches(cleanHyphen)) {
            return cleanHyphen
        }

        val firstPart = cleanHyphen.substringBefore('_').substringBefore('-')
        if (Regex("^[a-zA-Z0-9._]{1,30}$").matches(firstPart)) {
            return firstPart
        }

        return Regex("^[a-zA-Z0-9._]{1,30}").find(cleanHyphen)?.value ?: "instagram_media"
    }

    /**
     * Builds an HTML-formatted caption for Telegram messages.
     * Includes Instagram profile name, link to original Instagram profile,
     * lot and batch indices, and filename.
     */
    fun buildCaption(
        username: String,
        fileName: String,
        lotNumber: Int,
        totalLots: Int,
        groupItemRange: String? = null,
        includeLink: Boolean = true
    ): String {
        val safeUsername = escapeHtml(username)
        val safeFileName = escapeHtml(fileName)
        val sb = StringBuilder()

        sb.append("👤 <b>Perfil:</b> @$safeUsername\n")
        if (includeLink) {
            sb.append("🔗 <b>Instagram:</b> <a href=\"https://www.instagram.com/$safeUsername/\">instagram.com/$safeUsername</a>\n")
        }
        sb.append("📦 <b>Lote:</b> $lotNumber/$totalLots")
        if (!groupItemRange.isNullOrBlank()) {
            sb.append(" • <b>Itens:</b> $groupItemRange")
        }
        sb.append("\n📁 <code>$safeFileName</code>")

        return sb.toString()
    }

    /**
     * Builds an informational header message for a new Lot (batch of 100).
     */
    fun buildLotHeaderMessage(
        username: String,
        lotNumber: Int,
        totalLots: Int,
        itemCountInLot: Int,
        totalUserFiles: Int,
        lotTotalSizeBytes: Long
    ): String {
        val safeUsername = escapeHtml(username)
        val sizeFormatted = formatFileSize(lotTotalSizeBytes)
        return """
            🗂️ <b>INÍCIO DO LOTE DE ARQUIVAMENTO</b>
            ━━━━━━━━━━━━━━━━━━━━━
            👤 <b>Perfil Instagram:</b> @$safeUsername
            📦 <b>Lote:</b> $lotNumber de $totalLots
            📊 <b>Arquivos neste lote:</b> $itemCountInLot ($totalUserFiles no total)
            💾 <b>Tamanho do lote:</b> $sizeFormatted
            🔗 <a href="https://www.instagram.com/$safeUsername/">Acessar Perfil Original</a>
            ━━━━━━━━━━━━━━━━━━━━━
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }
}
