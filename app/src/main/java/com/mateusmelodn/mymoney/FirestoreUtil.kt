package com.mateusmelodn.mymoney

class FirestoreUtil {
    companion object {
        public val DUE_COLLECTION = "dues"
        public val DUE_FIELD_ID = "id"
        public val DUE_FIELD_VALUE = "value"
        public val DUE_FIELD_DESCRIPTION = "description"
        public val DUE_FIELD_DATE_TIME = "dateTime"
        public val DUE_FIELD_PAID = "paid"
        public val DUE_COLLECTION_ORDER_BY = "dateTime"

        public val REVENUE_COLLECTION = "revenues"
        public val REVENUE_FIELD_ID = "id"
        public val REVENUE_FIELD_VALUE = "value"
        public val REVENUE_FIELD_DESCRIPTION = "description"
        public val REVENUE_FIELD_DATE_TIME = "dateTime"
        public val REVENUE_FIELD_PAID = "paid"
        public val REVENUE_COLLECTION_ORDER_BY = "dateTime"

        public val LIMIT_ITEM_PER_QUERY = 50L
    }
}