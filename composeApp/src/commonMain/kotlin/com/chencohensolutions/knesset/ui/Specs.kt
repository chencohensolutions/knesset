package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.ui.committees.calendarSpec
import com.chencohensolutions.knesset.ui.committees.committeesSpec
import com.chencohensolutions.knesset.ui.committees.todayInIsrael
import com.chencohensolutions.knesset.ui.legislation.billsSpec
import com.chencohensolutions.knesset.ui.legislation.lawsSpec
import com.chencohensolutions.knesset.ui.legislation.secondaryLawsSpec
import com.chencohensolutions.knesset.ui.list.ListSpec
import com.chencohensolutions.knesset.ui.people.factionsSpec
import com.chencohensolutions.knesset.ui.people.ministriesSpec
import com.chencohensolutions.knesset.ui.people.personsSpec
import com.chencohensolutions.knesset.ui.plenum.motionsSpec
import com.chencohensolutions.knesset.ui.plenum.questionsSpec
import com.chencohensolutions.knesset.ui.plenum.sittingsSpec
import com.chencohensolutions.knesset.ui.plenum.votesSpec

/** The list behind each kind, for search "show all" and Home cards. */
fun specFor(kind: Kind): ListSpec<*>? = when (kind) {
    Kind.BILL -> billsSpec
    Kind.LAW -> lawsSpec
    Kind.SECONDARY_LAW -> secondaryLawsSpec
    Kind.VOTE -> votesSpec
    Kind.SITTING -> sittingsSpec
    Kind.MOTION -> motionsSpec
    Kind.QUESTION -> questionsSpec
    Kind.COMMITTEE -> committeesSpec
    Kind.COMMITTEE_SESSION -> calendarSpec(todayInIsrael())
    Kind.MEMBER -> personsSpec
    Kind.FACTION -> factionsSpec
    Kind.MINISTRY -> ministriesSpec
    Kind.LOBBYIST -> null
}
