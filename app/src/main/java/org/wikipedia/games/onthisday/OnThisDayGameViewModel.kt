package org.wikipedia.games.onthisday

import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.wikipedia.WikipediaApp
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.feed.onthisday.OnThisDay
import org.wikipedia.util.Resource
import java.time.LocalDate

class OnThisDayGameViewModel(bundle: Bundle) : ViewModel() {

    private val _gameState = MutableLiveData<Resource<GameState>>()
    val gameState: LiveData<Resource<GameState>> get() = _gameState

    private lateinit var currentState: GameState
    private val currentDate = LocalDate.now()
    private val currentMonth = currentDate.monthValue
    private val currentDay = currentDate.dayOfMonth

    private val events = mutableListOf<OnThisDay.Event>()

    init {
        loadGameState()
    }

    fun loadGameState() {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            _gameState.postValue(Resource.Error(throwable))
        }) {
            _gameState.postValue(Resource.Loading())

            events.clear()
            events.addAll(ServiceFactory.getRest(WikipediaApp.instance.wikiSite).getOnThisDay(currentMonth, currentDay).events)

            // TODO: load saved state from storage
            // otherwise, create a new state
            currentState = GameState(composeQuestionState(currentMonth, currentDay, 0))

            _gameState.postValue(Resource.Success(currentState))
        }
    }

    fun submitCurrentResponse(selectedYear: Int) {
        currentState = currentState.copy(currentQuestionState = currentState.currentQuestionState.copy(yearSelected = selectedYear))

        if (currentState.currentQuestionState.goToNext) {
            val nextQuestionIndex = currentState.currentQuestionIndex + 1

            currentState = currentState.copy(currentQuestionState = composeQuestionState(currentMonth, currentDay, nextQuestionIndex), currentQuestionIndex = nextQuestionIndex)
            _gameState.postValue(Resource.Success(currentState))

        } else {
            currentState = currentState.copy(currentQuestionState = currentState.currentQuestionState.copy(goToNext = true))

            val isCorrect = currentState.currentQuestionState.event.year == selectedYear
            if (isCorrect) {
                _gameState.postValue(CurrentQuestionCorrect(currentState))
            } else {
                _gameState.postValue(CurrentQuestionIncorrect(currentState))
            }
        }
    }

    private fun composeQuestionState(month: Int, day: Int, index: Int): QuestionState {
        val event = events[index]
        val yearChoices = mutableListOf<Int>().apply {
            add(event.year)
            repeat(3) {
                add((event.year - 10..event.year + 10).random())
            }
        }
        return QuestionState(event, yearChoices.shuffled())
    }

    data class GameState(
        val currentQuestionState: QuestionState,

        // TODO: everything below this line should be persisted

        val totalQuestions: Int = NUM_QUESTIONS,
        val currentQuestionIndex: Int = 0,

        // history of today's answers (correct vs incorrect)
        val answerState: List<Boolean> = List(NUM_QUESTIONS) { false },

        // map of:   year: month: day: list of answers
        val answerStateHistory: Map<Int, Map<Int, Map<Int, List<Boolean>>>> = emptyMap()
    )

    data class QuestionState(
        val event: OnThisDay.Event,
        val yearChoices: List<Int>,
        val yearSelected: Int? = null,
        val goToNext: Boolean = false
    )

    class CurrentQuestionCorrect(val data: GameState) : Resource<GameState>()
    class CurrentQuestionIncorrect(val data: GameState) : Resource<GameState>()

    class Factory(val bundle: Bundle) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnThisDayGameViewModel(bundle) as T
        }
    }

    companion object {
        const val NUM_QUESTIONS = 5
    }
}
