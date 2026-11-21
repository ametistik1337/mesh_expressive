package ru.mesh.expressive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import ru.mesh.expressive.data.local.SessionManager
import ru.mesh.expressive.data.model.*
import ru.mesh.expressive.data.remote.GitHubReleaseInfo
import ru.mesh.expressive.data.remote.GitHubUpdateManager
import ru.mesh.expressive.data.repository.AutoCompleteResult
import ru.mesh.expressive.data.repository.MeshRepository

enum class MainTab {
    DASHBOARD, SCHEDULE, HOMEWORK, MARKS, GIFTS, RATING, CLASSMATES, ATTENDANCE, MEALS, PORTFOLIO, SETTINGS, AUTH
}

enum class DashboardDay {
    TODAY, TOMORROW
}

enum class MarksViewMode {
    BY_SUBJECT, BY_DATE
}

enum class RatingPeriodMode(val title: String) {
    YEAR("За год"),
    SEMESTER("За полугодие"),
    MONTH("За месяц")
}

class MeshMainViewModel(
    val repository: MeshRepository,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _currentTab = MutableStateFlow(
        if (sessionManager.isLoggedIn) MainTab.DASHBOARD else MainTab.AUTH
    )
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _dashboardDay = MutableStateFlow(DashboardDay.TODAY)
    val dashboardDay: StateFlow<DashboardDay> = _dashboardDay.asStateFlow()

    private val _isAutoCompleting = MutableStateFlow(false)
    val isAutoCompleting: StateFlow<Boolean> = _isAutoCompleting.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isCompactSchedule = MutableStateFlow(sessionManager.isCompactSchedule)
    val isCompactSchedule: StateFlow<Boolean> = _isCompactSchedule.asStateFlow()

    private val _showWeightedGpa = MutableStateFlow(sessionManager.showWeightedGpa)
    val showWeightedGpa: StateFlow<Boolean> = _showWeightedGpa.asStateFlow()

    private val _hideCompletedQuests = MutableStateFlow(sessionManager.hideCompletedQuests)
    val hideCompletedQuests: StateFlow<Boolean> = _hideCompletedQuests.asStateFlow()

    private val _enableSpringPhysics = MutableStateFlow(sessionManager.enableSpringPhysics)
    val enableSpringPhysics: StateFlow<Boolean> = _enableSpringPhysics.asStateFlow()

    private val _hideEmptyScheduleDays = MutableStateFlow(sessionManager.hideEmptyScheduleDays)
    val hideEmptyScheduleDays: StateFlow<Boolean> = _hideEmptyScheduleDays.asStateFlow()

    private val _gpaTargetScore = MutableStateFlow(sessionManager.gpaTargetScore)
    val gpaTargetScore: StateFlow<Float> = _gpaTargetScore.asStateFlow()

    private val _autoRefreshMinutes = MutableStateFlow(sessionManager.autoRefreshIntervalMinutes)
    val autoRefreshMinutes: StateFlow<Int> = _autoRefreshMinutes.asStateFlow()

    private val _startTab = MutableStateFlow(sessionManager.startTab)
    val startTab: StateFlow<String> = _startTab.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(sessionManager.hapticFeedbackEnabled)
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    private val gitHubUpdateManager = GitHubUpdateManager()

    private val _isAutoUpdatesAndCounterEnabled = MutableStateFlow(sessionManager.isAutoUpdatesAndCounterEnabled)
    val isAutoUpdatesAndCounterEnabled: StateFlow<Boolean> = _isAutoUpdatesAndCounterEnabled.asStateFlow()

    private val _availableUpdate = MutableStateFlow<GitHubReleaseInfo?>(null)
    val availableUpdate: StateFlow<GitHubReleaseInfo?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _hideCompletedHomework = MutableStateFlow(sessionManager.hideCompletedHomework)
    val hideCompletedHomework: StateFlow<Boolean> = _hideCompletedHomework.asStateFlow()

    private val _themeMode = MutableStateFlow(sessionManager.themeMode)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isAmoledDark = MutableStateFlow(sessionManager.isAmoledDark)
    val isAmoledDark: StateFlow<Boolean> = _isAmoledDark.asStateFlow()

    private val _studyPeriodMode = MutableStateFlow(sessionManager.studyPeriodMode)
    val studyPeriodMode: StateFlow<String> = _studyPeriodMode.asStateFlow()

    private val _ratingPeriodMode = MutableStateFlow(RatingPeriodMode.YEAR)
    val ratingPeriodMode: StateFlow<RatingPeriodMode> = _ratingPeriodMode.asStateFlow()

    private val _semesterAcademicRanks = MutableStateFlow<List<AcademicClassRankItem>>(emptyList())
    val semesterAcademicRanks: StateFlow<List<AcademicClassRankItem>> = _semesterAcademicRanks.asStateFlow()

    val studentProfile: StateFlow<StudentProfile> = repository.studentProfile
    val weekSchedule: StateFlow<Map<String, List<LessonScheduleItem>>> = repository.weekSchedule
    val scheduleToday: StateFlow<List<LessonScheduleItem>> = repository.scheduleToday
    val scheduleTomorrow: StateFlow<List<LessonScheduleItem>> = repository.scheduleTomorrow
    val homeworkList: StateFlow<List<HomeworkItem>> = repository.homeworkList
    val subjectSummaries: StateFlow<List<SubjectSummary>> = repository.subjectSummaries
    val gamificationProfile: StateFlow<GamificationProfile> = repository.gamificationProfile
    val starLeaders: StateFlow<List<StarLeaderItem>> = repository.starLeaders
    val classmates: StateFlow<List<ClassmateItem>> = repository.classmates
    val rewards: StateFlow<List<RewardItem>> = repository.rewards
    val profileRewards: StateFlow<List<ProfileRewardItem>> = repository.profileRewards
    val works: StateFlow<List<WorkItem>> = repository.works
    val ratingInfo: StateFlow<RatingInfo> = repository.ratingInfo
    val academicClassRanks: StateFlow<List<AcademicClassRankItem>> = repository.academicClassRanks
    val mealsBalance: StateFlow<MealsBalance> = repository.mealsBalance
    val attendance: StateFlow<AttendanceSummary> = repository.attendance
    val periodsSchedules: StateFlow<List<PeriodScheduleItemDTO>> = repository.periodsSchedules
    val vacationPeriods: StateFlow<List<VacationPeriodInfo>> = repository.vacationPeriods
    val upcomingVacation: StateFlow<VacationPeriodInfo?> = repository.upcomingVacation
    val portfolioAchievements: StateFlow<List<PortfolioAchievementItem>> = repository.portfolioAchievements
    val isOffline: StateFlow<Boolean> = repository.isOffline

    val activeGpa: StateFlow<Double> = combine(
        studentProfile,
        ratingInfo
    ) { profile, rInfo ->
        if (profile.gpa > 0.0) {
            profile.gpa
        } else if (rInfo.score > 0) {
            rInfo.score / 20.0
        } else {
            0.0
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = studentProfile.value.gpa
    )

    private val _selectedRatingSubjectId = MutableStateFlow<Long?>(null)
    val selectedRatingSubjectId: StateFlow<Long?> = _selectedRatingSubjectId.asStateFlow()

    private val _subjectAcademicRanks = MutableStateFlow<List<AcademicClassRankItem>>(emptyList())
    val subjectAcademicRanks: StateFlow<List<AcademicClassRankItem>> = _subjectAcademicRanks.asStateFlow()

    private val _isSubjectRankLoading = MutableStateFlow(false)
    val isSubjectRankLoading: StateFlow<Boolean> = _isSubjectRankLoading.asStateFlow()

    fun selectRatingPeriodMode(mode: RatingPeriodMode) {
        _ratingPeriodMode.value = mode
        if (mode == RatingPeriodMode.SEMESTER) {
            loadSemesterRanks()
        } else if (_selectedRatingSubjectId.value != null) {
            selectRatingSubject(_selectedRatingSubjectId.value)
        }
    }

    private fun resolveSemesterTargetDate(): String {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val startYear = ru.mesh.expressive.util.DateUtils.getAcademicStartYear()
        val firstSemEnd = "$startYear-12-30"
        return if (today <= firstSemEnd) today else today
    }

    private fun loadSemesterRanks() {
        viewModelScope.launch {
            _isSubjectRankLoading.value = true
            try {
                val subjId = _selectedRatingSubjectId.value
                val subjName = subjId?.let { id -> subjectSummaries.value.find { it.subjectId == id }?.subject }
                val list = repository.fetchSubjectClassRank(subjId, subjName, resolveSemesterTargetDate())
                if (subjId != null) {
                    _subjectAcademicRanks.value = list
                } else {
                    _semesterAcademicRanks.value = list
                }
            } finally {
                _isSubjectRankLoading.value = false
            }
        }
    }

    fun selectRatingSubject(subjectId: Long?) {
        _selectedRatingSubjectId.value = subjectId
        if (subjectId == null) {
            _subjectAcademicRanks.value = emptyList()
            if (_ratingPeriodMode.value == RatingPeriodMode.SEMESTER && _semesterAcademicRanks.value.isEmpty()) {
                loadSemesterRanks()
            }
            return
        }
        viewModelScope.launch {
            _isSubjectRankLoading.value = true
            try {
                val subjName = subjectSummaries.value.find { it.subjectId == subjectId }?.subject
                val targetDate = if (_ratingPeriodMode.value == RatingPeriodMode.SEMESTER) resolveSemesterTargetDate() else null
                val list = repository.fetchSubjectClassRank(subjectId, subjName, targetDate)
                _subjectAcademicRanks.value = list
            } finally {
                _isSubjectRankLoading.value = false
            }
        }
    }

    private val _customFeedItems = MutableStateFlow<List<ProfileRewardItem>?>(null)
    val customFeedItems: StateFlow<List<ProfileRewardItem>?> = _customFeedItems.asStateFlow()

    private val _isFeedLoading = MutableStateFlow(false)
    val isFeedLoading: StateFlow<Boolean> = _isFeedLoading.asStateFlow()

    fun loadPersonalFeed() {
        viewModelScope.launch {
            _isFeedLoading.value = true
            try {
                val items = repository.fetchMyPersonalRewards()
                _customFeedItems.value = items
            } finally {
                _isFeedLoading.value = false
            }
        }
    }

    fun loadPersonFeedByPid(pid: Long) {
        viewModelScope.launch {
            _isFeedLoading.value = true
            try {
                val received = repository.fetchFeedRewards(targetProfileId = pid, from = "ALL", to = "ME")
                val sent = repository.fetchFeedRewards(targetProfileId = pid, from = "ME", to = "OTHERS")
                val combined = (received + sent).distinctBy { it.profileRewardId.takeIf { id -> id > 0 } ?: it.id }
                    .sortedByDescending { it.purchasedAt.orEmpty() }
                _customFeedItems.value = combined
            } finally {
                _isFeedLoading.value = false
            }
        }
    }

    fun clearCustomFeed() {
        _customFeedItems.value = null
    }

    private val _showOnboardingGuide = MutableStateFlow(!sessionManager.isOnboardingCompleted)
    val showOnboardingGuide: StateFlow<Boolean> = _showOnboardingGuide.asStateFlow()

    private val _marksViewMode = MutableStateFlow(
        if (sessionManager.marksViewMode == "BY_DATE") MarksViewMode.BY_DATE else MarksViewMode.BY_SUBJECT
    )
    val marksViewMode: StateFlow<MarksViewMode> = _marksViewMode.asStateFlow()

    fun setMarksViewMode(mode: MarksViewMode) {
        _marksViewMode.value = mode
        sessionManager.marksViewMode = mode.name
    }

    val oauthManager = ru.mesh.expressive.data.remote.OAuthManager(sessionManager)

    private val _isOAuthLoading = MutableStateFlow(false)
    val isOAuthLoading: StateFlow<Boolean> = _isOAuthLoading.asStateFlow()

    private val _oAuthError = MutableStateFlow<String?>(null)
    val oAuthError: StateFlow<String?> = _oAuthError.asStateFlow()

    fun startOAuthLogin(context: android.content.Context) {
        _isOAuthLoading.value = true
        _oAuthError.value = null
        viewModelScope.launch {
            val res = oauthManager.startAuthFlow(context)
            if (res.isFailure) {
                _isOAuthLoading.value = false
                _oAuthError.value = res.exceptionOrNull()?.localizedMessage ?: "Не удалось открыть браузер для авторизации"
            }
        }
    }

    fun handleAuthDeepLink(uri: android.net.Uri, onSuccess: () -> Unit = {}) {
        _isOAuthLoading.value = true
        _oAuthError.value = null
        viewModelScope.launch {
            val res = oauthManager.handleAuthRedirect(uri)
            _isOAuthLoading.value = false
            res.onSuccess { token ->
                saveAuthToken(token)
                onSuccess()
            }.onFailure { err ->
                _oAuthError.value = err.localizedMessage ?: "Ошибка авторизации"
            }
        }
    }

    fun resetOAuthState() {
        _isOAuthLoading.value = false
        _oAuthError.value = null
    }

    private val _isTokenRefreshing = MutableStateFlow(false)
    val isTokenRefreshing: StateFlow<Boolean> = _isTokenRefreshing.asStateFlow()

    private val _isTokenRefreshOverlayDismissed = MutableStateFlow(false)
    val isTokenRefreshOverlayDismissed: StateFlow<Boolean> = _isTokenRefreshOverlayDismissed.asStateFlow()

    private val _showReAuthDialog = MutableStateFlow(false)
    val showReAuthDialog: StateFlow<Boolean> = _showReAuthDialog.asStateFlow()

    fun triggerTokenRefresh() {
        if (!sessionManager.isLoggedIn || _isTokenRefreshing.value) return
        _isTokenRefreshing.value = true
        _isTokenRefreshOverlayDismissed.value = false
        viewModelScope.launch {
            val res = oauthManager.refreshTokens()
            res.onSuccess { newToken ->
                onTokenRefreshSuccess(newToken)
            }.onFailure {
                onTokenRefreshFailed()
            }
        }
    }

    fun dismissTokenRefreshOverlay() {
        _isTokenRefreshing.value = false
        _isTokenRefreshOverlayDismissed.value = true
        _showReAuthDialog.value = false
        _currentTab.value = MainTab.DASHBOARD
        repository.loadCachedData()
    }

    fun onTokenRefreshSuccess(newToken: String) {
        _isTokenRefreshing.value = false
        _isTokenRefreshOverlayDismissed.value = false
        saveAuthToken(newToken)
        refreshData()
    }

    fun onTokenRefreshFailed() {
        _isTokenRefreshing.value = false
        _isTokenRefreshOverlayDismissed.value = false
        _showReAuthDialog.value = true
        repository.loadCachedData()
    }

    fun dismissReAuthDialog() {
        _showReAuthDialog.value = false
        _currentTab.value = MainTab.DASHBOARD
        repository.loadCachedData()
    }

    init {
        viewModelScope.launch {
            repository.unauthorizedEvent.collect {
                triggerTokenRefresh()
            }
        }
        viewModelScope.launch {
            scheduleToday.collect { lessons ->
                if (lessons.isNotEmpty()) {
                    _dashboardDay.value = computeSmartDefaultDay(lessons)
                }
            }
        }
        if (sessionManager.isLoggedIn) {
            refreshData()
        }
        if (sessionManager.isAutoUpdatesAndCounterEnabled) {
            triggerBackgroundUpdateAndCounterCheck()
        }
    }

    fun triggerBackgroundUpdateAndCounterCheck() {
        viewModelScope.launch {
            try {
                val release = gitHubUpdateManager.checkForUpdates()
                if (release != null && release.tagName != sessionManager.dismissedUpdateVersion) {
                    _availableUpdate.value = release
                }
            } catch (_: Exception) {}

            try {
                val now = System.currentTimeMillis()
                if (now - sessionManager.lastCounterPingTimestamp >= 24 * 60 * 60 * 1000L) {
                    val schedules = sessionManager.cachedPeriodsSchedules.orEmpty()
                    val vacDates = schedules.filter { it.type == "vacation" || it.title.contains("каникул", ignoreCase = true) }
                        .map { it.date }
                    val success = gitHubUpdateManager.syncActiveCounter(sessionManager.anonymousInstallId, vacDates)
                    if (success) {
                        sessionManager.lastCounterPingTimestamp = now
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun toggleAutoUpdatesAndCounter(enabled: Boolean) {
        sessionManager.isAutoUpdatesAndCounterEnabled = enabled
        _isAutoUpdatesAndCounterEnabled.value = enabled
        if (enabled) {
            triggerBackgroundUpdateAndCounterCheck()
        }
    }

    fun checkForUpdatesManual(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (_isCheckingUpdate.value) return
        _isCheckingUpdate.value = true
        viewModelScope.launch {
            try {
                val release = gitHubUpdateManager.checkForUpdates()
                _isCheckingUpdate.value = false
                if (release != null) {
                    _availableUpdate.value = release
                    onResult(true, "Найдено обновление: ${release.tagName}")
                } else {
                    onResult(false, "У вас установлена последняя версия")
                }
            } catch (e: Exception) {
                _isCheckingUpdate.value = false
                onResult(false, "Ошибка проверки: ${e.localizedMessage ?: "нет сети"}")
            }
        }
    }

    fun dismissUpdateDialog(skipThisVersion: Boolean = false) {
        val current = _availableUpdate.value
        if (skipThisVersion && current != null) {
            sessionManager.dismissedUpdateVersion = current.tagName
        }
        _availableUpdate.value = null
    }

    fun completeOnboardingGuide() {
        sessionManager.isOnboardingCompleted = true
        _showOnboardingGuide.value = false
        if (sessionManager.isAutoUpdatesAndCounterEnabled) {
            triggerBackgroundUpdateAndCounterCheck()
        }
    }

    fun computeSmartDefaultDay(todayLessons: List<LessonScheduleItem>): DashboardDay {
        val cal = java.util.Calendar.getInstance()
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(java.util.Calendar.MINUTE)
        val currentTimeMinutes = currentHour * 60 + currentMinute

        val lastLesson = todayLessons.maxByOrNull { it.lessonNumber }
        if (lastLesson != null && lastLesson.endTime.isNotBlank()) {
            val parts = lastLesson.endTime.split(":")
            if (parts.size >= 2) {
                val endHour = parts[0].toIntOrNull() ?: 15
                val endMinute = parts[1].toIntOrNull() ?: 0
                val lessonEndMinutes = endHour * 60 + endMinute
                return if (currentTimeMinutes >= lessonEndMinutes) DashboardDay.TOMORROW else DashboardDay.TODAY
            }
        }
        return if (currentTimeMinutes >= 15 * 60) DashboardDay.TOMORROW else DashboardDay.TODAY
    }

    val isLoggedIn: Boolean
        get() = sessionManager.isLoggedIn

    val currentAuthToken: String?
        get() = sessionManager.authToken

    fun restartOnboardingGuide() {
        _showOnboardingGuide.value = true
    }

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
    }

    fun selectDashboardDay(day: DashboardDay) {
        _dashboardDay.value = day
    }

    fun toggleHomework(id: String) {
        repository.toggleHomework(id)
    }

    fun claimDailyGift(onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            val resultMessage = repository.claimDailyGift()
            onResult(resultMessage)
        }
    }

    fun toggleInfiniteStars(enabled: Boolean) {
        repository.setInfiniteStars(enabled)
    }

    fun toggleCompactSchedule(enabled: Boolean) {
        sessionManager.isCompactSchedule = enabled
        _isCompactSchedule.value = enabled
    }

    fun toggleShowWeightedGpa(enabled: Boolean) {
        sessionManager.showWeightedGpa = enabled
        _showWeightedGpa.value = enabled
        repository.refreshLiveGpaAndRating()
    }

    fun toggleHideCompletedQuests(enabled: Boolean) {
        sessionManager.hideCompletedQuests = enabled
        _hideCompletedQuests.value = enabled
    }

    fun toggleEnableSpringPhysics(enabled: Boolean) {
        sessionManager.enableSpringPhysics = enabled
        _enableSpringPhysics.value = enabled
    }

    fun toggleHideEmptyScheduleDays(enabled: Boolean) {
        sessionManager.hideEmptyScheduleDays = enabled
        _hideEmptyScheduleDays.value = enabled
    }

    fun setGpaTargetScore(target: Float) {
        sessionManager.gpaTargetScore = target
        _gpaTargetScore.value = target
    }

    fun setAutoRefreshMinutes(minutes: Int) {
        sessionManager.autoRefreshIntervalMinutes = minutes
        _autoRefreshMinutes.value = minutes
    }

    fun setStartTab(tabName: String) {
        sessionManager.startTab = tabName
        _startTab.value = tabName
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        sessionManager.hapticFeedbackEnabled = enabled
        _hapticFeedbackEnabled.value = enabled
    }

    fun toggleHideCompletedHomework(enabled: Boolean) {
        sessionManager.hideCompletedHomework = enabled
        _hideCompletedHomework.value = enabled
    }

    fun setThemeMode(mode: String) {
        sessionManager.themeMode = mode
        _themeMode.value = mode
    }

    fun toggleAmoledDark(enabled: Boolean) {
        sessionManager.isAmoledDark = enabled
        _isAmoledDark.value = enabled
    }

    fun setStudyPeriodMode(mode: String) {
        sessionManager.studyPeriodMode = mode
        _studyPeriodMode.value = mode
    }

    fun unlockReward(id: String) {
        repository.unlockReward(id)
    }

    fun sendGift(
        rewardId: String,
        costStars: Int,
        gamificationId: String,
        comment: String,
        isAnonymous: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val (success, message) = repository.sendGift(
                rewardId = rewardId,
                costStars = costStars,
                gamificationId = gamificationId,
                comment = comment,
                isAnonymous = isAnonymous
            )
            onResult(success, message)
        }
    }

    fun saveAuthToken(token: String) {
        viewModelScope.launch {
            repository.saveAuthToken(token)
            _currentTab.value = MainTab.DASHBOARD
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.fetchRemoteData()
            _isRefreshing.value = false
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
            } catch (_: Exception) {}
            repository.logout()
            sessionManager.clear()
            _currentTab.value = MainTab.AUTH
        }
    }

    fun logout() {
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        } catch (_: Exception) {}
        repository.logout()
        _currentTab.value = MainTab.AUTH
    }

    fun autoCompleteAllQuests(onCompleted: (AutoCompleteResult) -> Unit) {
        viewModelScope.launch {
            _isAutoCompleting.value = true
            val result = repository.autoCompleteAllQuests()
            _isAutoCompleting.value = false
            onCompleted(result)
        }
    }

    fun uploadAvatar(bytes: ByteArray, fileName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.uploadAvatar(bytes, fileName)
            onResult(success)
        }
    }

    fun deleteAvatar(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.deleteAvatar()
            onResult(success)
        }
    }

    private val _isGiftSendActive = MutableStateFlow(false)
    val isGiftSendActive: StateFlow<Boolean> = _isGiftSendActive.asStateFlow()

    private val _selectedGiftForSend = MutableStateFlow<RewardItem?>(null)
    val selectedGiftForSend: StateFlow<RewardItem?> = _selectedGiftForSend.asStateFlow()

    private val _targetRecipientGamifId = MutableStateFlow<String?>(null)
    val targetRecipientGamifId: StateFlow<String?> = _targetRecipientGamifId.asStateFlow()

    fun openGiftSend(gift: RewardItem? = null, recipientGamifId: String? = null) {
        _selectedGiftForSend.value = gift
        _targetRecipientGamifId.value = recipientGamifId
        _isGiftSendActive.value = true
    }

    fun closeGiftSend() {
        _isGiftSendActive.value = false
        _selectedGiftForSend.value = null
        _targetRecipientGamifId.value = null
    }

    private val _selectedLessonForDetails = MutableStateFlow<LessonScheduleItem?>(null)
    val selectedLessonForDetails: StateFlow<LessonScheduleItem?> = _selectedLessonForDetails.asStateFlow()

    private val _isLessonDetailsLoading = MutableStateFlow(false)
    val isLessonDetailsLoading: StateFlow<Boolean> = _isLessonDetailsLoading.asStateFlow()

    private val _selectedMarkLesson = MutableStateFlow<LessonScheduleItem?>(null)
    val selectedMarkLesson: StateFlow<LessonScheduleItem?> = _selectedMarkLesson.asStateFlow()

    private val _selectedMarkDetailed = MutableStateFlow<DetailedMarkResponseDTO?>(null)
    val selectedMarkDetailed: StateFlow<DetailedMarkResponseDTO?> = _selectedMarkDetailed.asStateFlow()

    private val _isMarkDetailedLoading = MutableStateFlow(false)
    val isMarkDetailedLoading: StateFlow<Boolean> = _isMarkDetailedLoading.asStateFlow()

    private val _markSubjectRanks = MutableStateFlow<List<AcademicClassRankItem>>(emptyList())
    val markSubjectRanks: StateFlow<List<AcademicClassRankItem>> = _markSubjectRanks.asStateFlow()

    private val _isMarkSubjectRanksLoading = MutableStateFlow(false)
    val isMarkSubjectRanksLoading: StateFlow<Boolean> = _isMarkSubjectRanksLoading.asStateFlow()

    fun openLessonDetails(lesson: LessonScheduleItem) {
        _selectedLessonForDetails.value = lesson
        val lessonIdLong = lesson.id.replace("ev_", "").substringBefore("_").toLongOrNull()
        if (lessonIdLong != null && lessonIdLong > 0) {
            viewModelScope.launch {
                _isLessonDetailsLoading.value = true
                val detailed = repository.fetchLessonDetails(lessonIdLong)
                if (detailed != null) {
                    _selectedLessonForDetails.value = lesson.copy(
                        room = if (detailed.room.isNotBlank()) detailed.room else lesson.room,
                        teacherName = if (detailed.teacherName.isNotBlank() && detailed.teacherName != "Учитель") detailed.teacherName else lesson.teacherName,
                        homework = detailed.homework ?: lesson.homework,
                        topic = detailed.topic ?: lesson.topic,
                        mark = detailed.mark ?: lesson.mark,
                        rawMark = detailed.rawMark ?: lesson.rawMark,
                        isPoint = detailed.isPoint || lesson.isPoint,
                        pointDate = detailed.pointDate ?: lesson.pointDate,
                        markId = detailed.markId ?: lesson.markId,
                        markWeight = if (detailed.mark != null) detailed.markWeight else lesson.markWeight,
                        markComment = detailed.markComment ?: lesson.markComment,
                        markControlForm = detailed.markControlForm ?: lesson.markControlForm,
                        markCreatedAt = detailed.markCreatedAt ?: lesson.markCreatedAt,
                        testMaterials = detailed.testMaterials,
                        fileMaterials = detailed.fileMaterials,
                        materialsCount = detailed.fileMaterials.size + detailed.testMaterials.size
                    )
                }
                _isLessonDetailsLoading.value = false
            }
        }
    }

    fun closeLessonDetails() {
        _selectedLessonForDetails.value = null
    }

    fun openMarkDetails(lesson: LessonScheduleItem) {
        _selectedMarkLesson.value = lesson
        _selectedMarkDetailed.value = null
        val targetMarkId = lesson.markId ?: lesson.id.replace("ev_", "").substringBefore("_").toLongOrNull()
        viewModelScope.launch {
            _isMarkSubjectRanksLoading.value = true
            _isMarkDetailedLoading.value = true
            try {
                if (targetMarkId != null && targetMarkId > 0) {
                    val detailed = repository.fetchMarkDetails(targetMarkId)
                    _selectedMarkDetailed.value = detailed
                }
            } catch (_: Exception) {
            } finally {
                _isMarkDetailedLoading.value = false
            }

            val ranks = repository.fetchLessonClassRank(lesson)
            _markSubjectRanks.value = ranks
            _isMarkSubjectRanksLoading.value = false
        }
    }

    fun openMarkDetails(mark: ru.mesh.expressive.data.model.MarkItem) {
        val pseudoLesson = LessonScheduleItem(
            id = mark.id,
            subject = mark.subject,
            subjectId = mark.subjectId,
            date = mark.date,
            mark = if (mark.value in 2..5) mark.value else null,
            rawMark = mark.rawValue,
            isPoint = mark.isPoint,
            pointDate = mark.pointDate,
            markId = mark.id.toLongOrNull(),
            markWeight = mark.weight,
            markComment = mark.comment,
            markControlForm = mark.controlFormName,
            markCreatedAt = mark.createdAt,
            isPointCorrected = mark.isPointCorrected,
            hasCorrection = mark.hasCorrection
        )
        openMarkDetails(pseudoLesson)
    }

    fun closeMarkDetails() {
        _selectedMarkLesson.value = null
        _selectedMarkDetailed.value = null
        _markSubjectRanks.value = emptyList()
    }

    private val _downloadedFileNames = MutableStateFlow<Set<String>>(emptySet())
    val downloadedFileNames: StateFlow<Set<String>> = _downloadedFileNames.asStateFlow()

    private val _currentlyDownloadingUrl = MutableStateFlow<String?>(null)
    val currentlyDownloadingUrl: StateFlow<String?> = _currentlyDownloadingUrl.asStateFlow()

    fun refreshDownloadedFiles(context: android.content.Context) {
        _downloadedFileNames.value = ru.mesh.expressive.util.MeshFileDownloader.getDownloadedFileNames(context)
    }

    private val _selectedHomeworkForDetails = MutableStateFlow<ru.mesh.expressive.data.model.HomeworkItem?>(null)
    val selectedHomeworkForDetails: StateFlow<ru.mesh.expressive.data.model.HomeworkItem?> = _selectedHomeworkForDetails.asStateFlow()

    fun openHomeworkDetails(homework: ru.mesh.expressive.data.model.HomeworkItem) {
        _selectedHomeworkForDetails.value = homework
        val lessonId = homework.lessonId
        if (lessonId != null && lessonId > 0 && homework.teacherFiles.isEmpty()) {
            viewModelScope.launch {
                val detailed = repository.fetchLessonDetails(lessonId)
                if (detailed != null && detailed.fileMaterials.isNotEmpty()) {
                    val current = _selectedHomeworkForDetails.value
                    if (current?.id == homework.id) {
                        _selectedHomeworkForDetails.value = current.copy(
                            teacherFiles = detailed.fileMaterials,
                            materialsCount = detailed.fileMaterials.size
                        )
                    }
                }
            }
        }
    }

    fun closeHomeworkDetails() {
        _selectedHomeworkForDetails.value = null
    }

    fun openAttachment(context: android.content.Context, url: String, fileName: String) {
        refreshDownloadedFiles(context)
        if (ru.mesh.expressive.util.MeshFileDownloader.isDownloaded(context, fileName)) {
            val file = ru.mesh.expressive.util.MeshFileDownloader.getAttachmentFile(context, fileName)
            val uri = ru.mesh.expressive.util.MeshFileDownloader.getFileUri(context, file)
            val mime = ru.mesh.expressive.util.MeshFileDownloader.getMimeType(fileName)
            ru.mesh.expressive.util.MeshFileDownloader.openFile(context, uri, mime, fileName)
        } else {
            downloadAttachment(context, url, fileName, openAfterDownload = true)
        }
    }

    fun shareAttachment(context: android.content.Context, fileName: String) {
        ru.mesh.expressive.util.MeshFileDownloader.shareFile(context, fileName)
    }

    private val _isAttachmentUploading = MutableStateFlow(false)
    val isAttachmentUploading: StateFlow<Boolean> = _isAttachmentUploading.asStateFlow()

    fun uploadHomeworkAttachment(homeworkEntryStudentId: Long, fileUri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isAttachmentUploading.value = true
            repository.uploadHomeworkAttachment(homeworkEntryStudentId, fileUri, context)
            _isAttachmentUploading.value = false
            _selectedHomeworkForDetails.value = homeworkList.value.find { it.homeworkEntryStudentId == homeworkEntryStudentId }
        }
    }

    fun deleteHomeworkAttachment(homeworkEntryStudentId: Long, fileId: Long) {
        viewModelScope.launch {
            repository.deleteHomeworkAttachment(homeworkEntryStudentId, fileId)
            _selectedHomeworkForDetails.value = homeworkList.value.find { it.homeworkEntryStudentId == homeworkEntryStudentId }
        }
    }

    private val _activeTestExecutionUrl = MutableStateFlow<String?>(null)
    val activeTestExecutionUrl: StateFlow<String?> = _activeTestExecutionUrl.asStateFlow()

    private val _activeTestExecutionTitle = MutableStateFlow<String?>(null)
    val activeTestExecutionTitle: StateFlow<String?> = _activeTestExecutionTitle.asStateFlow()

    fun openTestExecution(url: String, title: String = "Тестовое задание") {
        _activeTestExecutionTitle.value = title
        viewModelScope.launch {
            val resolvedUrl = repository.resolveTestLaunchUrl(url)
            _activeTestExecutionUrl.value = resolvedUrl
        }
    }

    fun closeTestExecution() {
        _activeTestExecutionUrl.value = null
        _activeTestExecutionTitle.value = null
    }

    private val _isDownloadingFile = MutableStateFlow(false)
    val isDownloadingFile: StateFlow<Boolean> = _isDownloadingFile.asStateFlow()

    private val _downloadStatusMessage = MutableStateFlow<String?>(null)
    val downloadStatusMessage: StateFlow<String?> = _downloadStatusMessage.asStateFlow()

    fun clearDownloadStatus() {
        _downloadStatusMessage.value = null
    }

    fun downloadAttachment(context: android.content.Context, url: String, fileName: String, openAfterDownload: Boolean = false) {
        viewModelScope.launch {
            _isDownloadingFile.value = true
            _currentlyDownloadingUrl.value = url
            val result = ru.mesh.expressive.util.MeshFileDownloader.downloadFile(
                context = context,
                url = url,
                suggestedFileName = fileName,
                sessionManager = repository.sessionManager
            )
            _isDownloadingFile.value = false
            _currentlyDownloadingUrl.value = null
            when (result) {
                is ru.mesh.expressive.util.DownloadResult.Success -> {
                    _downloadedFileNames.value = _downloadedFileNames.value + result.fileName
                    _downloadStatusMessage.value = "Файл сохранен: ${result.fileName}"
                    if (openAfterDownload) {
                        ru.mesh.expressive.util.MeshFileDownloader.openFile(context, result.uri, result.mimeType, result.fileName)
                    } else {
                        android.widget.Toast.makeText(context, "Файл сохранен: ${result.fileName}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                is ru.mesh.expressive.util.DownloadResult.Error -> {
                    _downloadStatusMessage.value = "Ошибка: ${result.message}"
                    android.widget.Toast.makeText(context, "Ошибка: ${result.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun loadScheduleForDates(beginDate: String, endDate: String) {
        viewModelScope.launch {
            repository.loadScheduleForDateRange(beginDate, endDate)
        }
    }

    class Factory(
        private val repository: MeshRepository,
        private val sessionManager: SessionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MeshMainViewModel::class.java)) {
                return MeshMainViewModel(repository, sessionManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
