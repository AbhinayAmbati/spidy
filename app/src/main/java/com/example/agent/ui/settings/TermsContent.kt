package com.example.agent.ui.settings

/**
 * Terms of Service content.
 *
 * THIS IS A TEMPLATE, NOT LEGAL ADVICE. Before shipping, replace the placeholders
 * below and have a qualified lawyer in your jurisdiction review it — particularly
 * the liability, indemnity and governing-law clauses, which are not enforceable in
 * the same way everywhere.
 */
object TermsContent {

    // ---- Replace these before publishing ----
    private const val ENTITY = "[LEGAL ENTITY NAME]"
    private const val JURISDICTION = "[JURISDICTION]"
    private const val CONTACT = "[CONTACT EMAIL]"

    const val LAST_UPDATED = "8 October 2026"

    data class Section(val title: String, val body: String)

    val SECTIONS: List<Section> = listOf(
        Section(
            "1. Acceptance",
            "By installing, opening or using Spidey you agree to be bound by these Terms. " +
                "If you do not agree, do not use the app and uninstall it.",
        ),
        Section(
            "2. Definitions",
            "\"Spidey\" means this application. \"We\", \"us\" and \"the Developer\" mean " +
                "$ENTITY. \"You\" means the individual using the application. \"Providers\" " +
                "means the third-party artificial-intelligence services you choose to connect.",
        ),
        Section(
            "3. What Spidey is, and is not",
            "Spidey is a local tool that uses Android's Accessibility framework to perform " +
                "actions on your own device, at your direction. It is not a hosted service, and " +
                "it is not a substitute for your own judgement. It does not provide legal, " +
                "financial, medical or other professional advice. Output produced by an " +
                "artificial-intelligence model may be wrong, incomplete, out of date or " +
                "inappropriate, and you must review it before relying on it.",
        ),
        Section(
            "4. Your credentials and your costs",
            "You supply your own Provider API key. You are solely responsible for that key, " +
                "for keeping it secure, and for all usage and charges incurred on your Provider " +
                "account. We do not receive, store or transmit your key to any party other than " +
                "the Provider you configure. We are not responsible for any charge, overage, " +
                "suspension or account action taken by a Provider.",
        ),
        Section(
            "5. Providers are independent third parties",
            "Providers are not affiliated with, endorsed by, sponsored by or acting as agents " +
                "of the Developer. Your use of a Provider is governed solely by that Provider's " +
                "own terms and policies. We make no representation or warranty whatsoever " +
                "regarding any Provider's availability, accuracy, pricing, reliability or data " +
                "handling practices.",
        ),
        Section(
            "6. You are responsible for actions performed",
            "You direct the agent, and every action it performs is initiated by your " +
                "instruction. You are solely and entirely responsible for the consequences of " +
                "any action performed on your device, including messages sent, calls placed, " +
                "purchases made, data deleted, permissions changed, or content shared. Do not " +
                "instruct the agent to do anything you would not do yourself.",
        ),
        Section(
            "7. Acceptable use",
            "You must not use Spidey: in violation of any applicable law or regulation; to " +
                "access accounts, devices or data you do not own or have permission to access; " +
                "to circumvent, disable or defeat any security or access-control mechanism; to " +
                "send spam, harass, defraud or deceive any person; to infringe the rights of " +
                "any third party; to violate any Provider's terms of service; or in any way " +
                "that harms a minor. We may discontinue support for, or distribution of, the " +
                "application at any time.",
        ),
        Section(
            "8. No warranty",
            "THE APPLICATION IS PROVIDED \"AS IS\" AND \"AS AVAILABLE\", WITHOUT WARRANTY OF " +
                "ANY KIND, WHETHER EXPRESS, IMPLIED OR STATUTORY, INCLUDING WITHOUT LIMITATION " +
                "ANY WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, ACCURACY, " +
                "QUIET ENJOYMENT OR NON-INFRINGEMENT. WE DO NOT WARRANT THAT THE APPLICATION " +
                "WILL BE UNINTERRUPTED, TIMELY, SECURE OR ERROR-FREE, OR THAT ANY TASK WILL BE " +
                "COMPLETED SUCCESSFULLY. Android imposes restrictions on what any application " +
                "may automate, and some tasks will be impossible; this is expected and is not a " +
                "defect.",
        ),
        Section(
            "9. Limitation of liability",
            "TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THE DEVELOPER'S TOTAL " +
                "AGGREGATE LIABILITY ARISING OUT OF OR RELATING TO THE APPLICATION SHALL BE " +
                "ZERO. IN NO EVENT SHALL THE DEVELOPER BE LIABLE FOR ANY INDIRECT, INCIDENTAL, " +
                "SPECIAL, CONSEQUENTIAL, EXEMPLARY OR PUNITIVE DAMAGES, OR FOR ANY LOSS OF " +
                "PROFITS, REVENUE, DATA, GOODWILL, OPPORTUNITY OR ACCOUNT ACCESS, HOWEVER " +
                "CAUSED AND UNDER ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, TORT " +
                "(INCLUDING NEGLIGENCE), STRICT LIABILITY OR OTHERWISE, EVEN IF ADVISED OF THE " +
                "POSSIBILITY OF SUCH DAMAGE. Where a jurisdiction does not permit the exclusion " +
                "of certain warranties or liabilities, those exclusions apply to the fullest " +
                "extent that jurisdiction permits, and nothing in these Terms limits any right " +
                "that cannot lawfully be limited.",
        ),
        Section(
            "10. Indemnity",
            "You agree to indemnify, defend and hold harmless the Developer from and against " +
                "any claim, demand, loss, liability, cost or expense (including reasonable legal " +
                "fees) arising out of or relating to your use of the application, your breach of " +
                "these Terms, or your violation of any law or third-party right.",
        ),
        Section(
            "11. Data and privacy",
            "The application stores your Provider key in encrypted storage on your device, and " +
                "stores task history and preferences locally. A summary of on-screen elements is " +
                "sent to the Provider you configure, and is handled under that Provider's terms. " +
                "We operate no servers and collect no personal data. You can clear local history " +
                "and memory, and delete your saved key, from Settings at any time.",
        ),
        Section(
            "12. Availability and support",
            "The application is provided free of charge. It may be modified, suspended or " +
                "discontinued at any time, without notice. We assume no obligation to provide " +
                "support, maintenance, updates or fixes.",
        ),
        Section(
            "13. Changes to these Terms",
            "We may update these Terms from time to time. The revised version takes effect when " +
                "published within the application, and the \"last updated\" date above will " +
                "change. Your continued use after that constitutes acceptance.",
        ),
        Section(
            "14. Governing law and disputes",
            "These Terms are governed by the laws of $JURISDICTION, without regard to its " +
                "conflict-of-laws rules. You and the Developer submit to the exclusive " +
                "jurisdiction of the courts of $JURISDICTION for any dispute arising out of or " +
                "relating to these Terms or the application.",
        ),
        Section(
            "15. Severability and entire agreement",
            "If any provision of these Terms is held unenforceable, that provision will be " +
                "limited or removed to the minimum extent necessary and the remaining provisions " +
                "will stay in full force. These Terms constitute the entire agreement between " +
                "you and the Developer regarding the application.",
        ),
        Section(
            "16. Contact",
            "Questions about these Terms can be sent to $CONTACT.",
        ),
    )
}
