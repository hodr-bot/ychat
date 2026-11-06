package org.ychat

import android.content.SharedPreferences

/**
 * Presets serveurs par défaut, recopiés à l'identique de l'upstream.
 *
 * Sources (clones scratch, sondage lecture seule) :
 * - SMP (11 actifs) : simplex-chat `src/Simplex/Chat/Operators/Presets.hs`
 *   -> `enabledSimplexChatSMPServers` (smp8..smp12, smp14..smp19, pas de smp13).
 * - XFTP (6) : simplexmq `src/Simplex/FileTransfer/Client/Presets.hs`
 *   -> `defaultXFTPServers` (xftp1..xftp6.simplex.im).
 * - NTF (2) : simplex-chat `src/Simplex/Chat/Library/Commands.hs:147`
 *   -> `_defaultNtfServers` (ntf3, ntf4 ; ntf2 commenté, exclu).
 *
 * Exclusions volontaires (voir rapport .worker-report.md, non-faits explicites) :
 * - `disabledSimplexChatSMPServers` (smp4, smp5, smp6.simplex.im).
 * - `fluxSMPServers_` (smp1..smp6.simplexonflux.com).
 * - `fluxXFTPServers` (xftp1..xftp6.simplexonflux.com).
 * - `simplexChatRelays` (3 relais https://smp5,smp6,smp4.simplex.im/r#...).
 * - serveur de test smp7 (dans `allPresetServers` uniquement).
 */
object ServerPresets {

    const val PREFS_KEY_SMP = "servers_smp"
    const val PREFS_KEY_XFTP = "servers_xftp"
    const val PREFS_KEY_NTF = "servers_ntf"

    /** 11 SMP SimpleX actifs (enabledSimplexChatSMPServers). */
    val DEFAULT_SMP_SERVERS: List<String> = listOf(
        "smp://0YuTwO05YJWS8rkjn9eLJDjQhFKvIYd8d4xG8X1blIU=@smp8.simplex.im,beccx4yfxxbvyhqypaavemqurytl6hozr47wfc7uuecacjqdvwpw2xid.onion",
        "smp://SkIkI6EPd2D63F4xFKfHk7I1UGZVNn6k1QWZ5rcyr6w=@smp9.simplex.im,jssqzccmrcws6bhmn77vgmhfjmhwlyr3u7puw4erkyoosywgl67slqqd.onion",
        "smp://6iIcWT_dF2zN_w5xzZEY7HI2Prbh3ldP07YTyDexPjE=@smp10.simplex.im,rb2pbttocvnbrngnwziclp2f4ckjq65kebafws6g4hy22cdaiv5dwjqd.onion",
        "smp://1OwYGt-yqOfe2IyVHhxz3ohqo3aCCMjtB-8wn4X_aoY=@smp11.simplex.im,6ioorbm6i3yxmuoezrhjk6f6qgkc4syabh7m3so74xunb5nzr4pwgfqd.onion",
        "smp://UkMFNAXLXeAAe0beCa4w6X_zp18PwxSaSjY17BKUGXQ=@smp12.simplex.im,ie42b5weq7zdkghocs3mgxdjeuycheeqqmksntj57rmejagmg4eor5yd.onion",
        "smp://enEkec4hlR3UtKx2NMpOUK_K4ZuDxjWBO1d9Y4YXVaA=@smp14.simplex.im,aspkyu2sopsnizbyfabtsicikr2s4r3ti35jogbcekhm3fsoeyjvgrid.onion",
        "smp://h--vW7ZSkXPeOUpfxlFGgauQmXNFOzGoizak7Ult7cw=@smp15.simplex.im,oauu4bgijybyhczbnxtlggo6hiubahmeutaqineuyy23aojpih3dajad.onion",
        "smp://hejn2gVIqNU6xjtGM3OwQeuk8ZEbDXVJXAlnSBJBWUA=@smp16.simplex.im,p3ktngodzi6qrf7w64mmde3syuzrv57y55hxabqcq3l5p6oi7yzze6qd.onion",
        "smp://ZKe4uxF4Z_aLJJOEsC-Y6hSkXgQS5-oc442JQGkyP8M=@smp17.simplex.im,ogtwfxyi3h2h5weftjjpjmxclhb5ugufa5rcyrmg7j4xlch7qsr5nuqd.onion",
        "smp://PtsqghzQKU83kYTlQ1VKg996dW4Cw4x_bvpKmiv8uns=@smp18.simplex.im,lyqpnwbs2zqfr45jqkncwpywpbtq7jrhxnib5qddtr6npjyezuwd3nqd.onion",
        "smp://N_McQS3F9TGoh4ER0QstUf55kGnNSd-wXfNPZ7HukcM=@smp19.simplex.im,i53bbtoqhlc365k6kxzwdp5w3cdt433s7bwh3y32rcbml2vztiyyz5id.onion"
    )

    /** 6 XFTP SimpleX (defaultXFTPServers). */
    val DEFAULT_XFTP_SERVERS: List<String> = listOf(
        "xftp://da1aH3nOT-9G8lV7bWamhxpDYdJ1xmW7j3JpGaDR5Ug=@xftp1.simplex.im,blisztokwh6alt5oem45f2eksm2xdowxszmhjesntcyolmhutoon7sid.onion",
        "xftp://5vog2Imy1ExJB_7zDZrkV1KDWi96jYFyy9CL6fndBVw=@xftp2.simplex.im,jk6jybnel5hin2am5omd7g3hyvb4tfkyl7ea4vx5ldi7gpvz5ogqtcqd.onion",
        "xftp://PYa32DdYNFWi0uZZOprWQoQpIk5qyjRJ3EF7bVpbsn8=@xftp3.simplex.im,pqhchr4bpkzef5wra4hnmzvppn5j5mjrpzkh5ixx6mhylnxxd6lmwnad.onion",
        "xftp://k_GgQl40UZVV0Y4BX9ZTyMVqX5ZewcLW0waQIl7AYDE=@xftp4.simplex.im,q2lrjgiyo2efyweksavwxh3dlsrxetaxjztde2x5nateugrtrwzvmdid.onion",
        "xftp://-bIo6o8wuVc4wpZkZD3tH-rCeYaeER_0lz1ffQcSJDs=@xftp5.simplex.im,2kikdbdhlrluburwt6q6rnoxvezcm2ifx2hj463jlunkeftlzm7hf3qd.onion",
        "xftp://6nSvtY9pJn6PXWTAIMNl95E1Kk1vD7FM2TeOA64CFLg=@xftp6.simplex.im,tg3fmu6houq7vfqlsipm2qqzhtpgxesnuydqozx3khojxydhs53sbbyd.onion"
    )

    /** 2 NTF SimpleX (_defaultNtfServers). */
    val DEFAULT_NTF_SERVERS: List<String> = listOf(
        "ntf://KmpZNNXiVZJx_G2T7jRUmDFxWXM3OAnunz3uLT0tqAA=@ntf3.simplex.im,pxculznuryunjdvtvh6s6szmanyadumpbmvevgdpe4wk5c65unyt4yid.onion",
        "ntf://CJ5o7X6fCxj2FFYRU2KuCo70y4jSqz7td2HYhLnXWbU=@ntf4.simplex.im,wtvuhdj26jwprmomnyfu5wfuq2hjkzfcc72u44vi6gdhrwxldt6xauad.onion"
    )

    /** Valeurs par défaut groupées. */
    fun defaults(): YChatServers = YChatServers(
        smp = DEFAULT_SMP_SERVERS,
        xftp = DEFAULT_XFTP_SERVERS,
        ntf = DEFAULT_NTF_SERVERS
    )

    /**
     * Charge les listes depuis SharedPreferences.
     * Part des défauts si une clé est absente (première init).
     */
    fun load(prefs: SharedPreferences): YChatServers {
        val smp = readList(prefs, PREFS_KEY_SMP) ?: DEFAULT_SMP_SERVERS
        val xftp = readList(prefs, PREFS_KEY_XFTP) ?: DEFAULT_XFTP_SERVERS
        val ntf = readList(prefs, PREFS_KEY_NTF) ?: DEFAULT_NTF_SERVERS
        return YChatServers(smp = smp, xftp = xftp, ntf = ntf)
    }

    /** Persiste une liste modifiable par l'utilisateur. */
    fun save(prefs: SharedPreferences, servers: YChatServers) {
        prefs.edit()
            .putString(PREFS_KEY_SMP, servers.smp.joinToString("\n"))
            .putString(PREFS_KEY_XFTP, servers.xftp.joinToString("\n"))
            .putString(PREFS_KEY_NTF, servers.ntf.joinToString("\n"))
            .apply()
    }

    private fun readList(prefs: SharedPreferences, key: String): List<String>? {
        if (!prefs.contains(key)) return null
        val raw = prefs.getString(key, null) ?: return null
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** Listes de serveurs (modifiables par l'utilisateur via [ServerPresets.save]). */
data class YChatServers(
    val smp: List<String>,
    val xftp: List<String>,
    val ntf: List<String>
)
