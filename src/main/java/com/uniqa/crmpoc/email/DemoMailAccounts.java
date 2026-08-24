package com.uniqa.crmpoc.email;

import java.util.List;

/**
 * Single source of truth for the demo's simulated mailbox roster (POC stand-in
 * for the ~20-30 real UNIQA department/branch inboxes). Both GreenMailTestServerConfig
 * (registers each as an IMAP user so it can actually receive mail) and
 * MailAccountSeeder (persists the matching MailAccount row the app reasons about)
 * read from this list, so the two never drift apart.
 *
 * Only a handful are canSend=true - the addresses an agent is actually allowed
 * to reply from. The rest are receive-only department/branch aliases, same as
 * a real org where most inboxes are monitored but not everyone's a "From".
 */
final class DemoMailAccounts {

    private DemoMailAccounts() {
    }

    record Def(String address, String displayName, boolean canSend) {}

    static final List<Def> ALL = List.of(
            new Def("claims-inbox@uniqa-poc.local", "Claims Intake", true),
            new Def("complaints@uniqa-poc.local", "Complaints Desk", true),
            new Def("policy-changes@uniqa-poc.local", "Policy Changes", true),
            new Def("general-inquiry@uniqa-poc.local", "General Inquiries", true),
            new Def("support@uniqa-poc.local", "Customer Support", true),

            new Def("sales@uniqa-poc.local", "Sales", false),
            new Def("underwriting@uniqa-poc.local", "Underwriting", false),
            new Def("renewals@uniqa-poc.local", "Renewals", false),
            new Def("billing@uniqa-poc.local", "Billing", false),
            new Def("legal@uniqa-poc.local", "Legal", false),
            new Def("hr@uniqa-poc.local", "HR", false),
            new Def("it-helpdesk@uniqa-poc.local", "IT Helpdesk", false),
            new Def("marketing@uniqa-poc.local", "Marketing", false),
            new Def("partnerships@uniqa-poc.local", "Partnerships", false),
            new Def("fraud-review@uniqa-poc.local", "Fraud Review", false),
            new Def("vienna.branch@uniqa-poc.local", "Vienna Branch", false),
            new Def("graz.branch@uniqa-poc.local", "Graz Branch", false),
            new Def("linz.branch@uniqa-poc.local", "Linz Branch", false),
            new Def("salzburg.branch@uniqa-poc.local", "Salzburg Branch", false),
            new Def("innsbruck.branch@uniqa-poc.local", "Innsbruck Branch", false),
            new Def("klagenfurt.branch@uniqa-poc.local", "Klagenfurt Branch", false),
            new Def("bregenz.branch@uniqa-poc.local", "Bregenz Branch", false),
            new Def("wels.branch@uniqa-poc.local", "Wels Branch", false),
            new Def("st-poelten.branch@uniqa-poc.local", "St. Poelten Branch", false)
    );
}
