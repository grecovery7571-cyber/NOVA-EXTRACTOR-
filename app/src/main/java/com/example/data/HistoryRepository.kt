package com.example.data

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val historyList: Flow<List<HistoryEntity>> = historyDao.getHistoryList()

    suspend fun insertHistory(item: HistoryEntity) {
        historyDao.insertHistory(item)
    }

    suspend fun deleteHistoryById(id: Int) {
        historyDao.deleteHistoryById(id)
    }

    suspend fun clearHistory() {
        historyDao.clearHistory()
    }
}
