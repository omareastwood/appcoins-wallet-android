package com.asfoundation.wallet.topup

import com.appcoins.wallet.bdsbilling.repository.BdsRepository
import com.appcoins.wallet.bdsbilling.repository.entity.Gateway
import com.appcoins.wallet.bdsbilling.repository.entity.PaymentMethodEntity
import com.appcoins.wallet.gamification.repository.ForecastBonusAndLevel
import com.asfoundation.wallet.backup.NotificationNeeded
import com.asfoundation.wallet.service.LocalCurrencyConversionService
import com.asfoundation.wallet.support.SupportInteractor
import com.asfoundation.wallet.topup.paymentMethods.PaymentMethodData
import com.asfoundation.wallet.ui.gamification.GamificationInteractor
import com.asfoundation.wallet.ui.iab.FiatValue
import com.asfoundation.wallet.ui.iab.InAppPurchaseInteractor
import com.asfoundation.wallet.wallet_blocked.WalletBlockedInteract
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import java.math.BigDecimal
import java.util.*
import kotlin.collections.ArrayList

class TopUpInteractor(private val repository: BdsRepository,
                      private val conversionService: LocalCurrencyConversionService,
                      private val gamificationInteractor: GamificationInteractor,
                      private val topUpValuesService: TopUpValuesService,
                      private val chipValueIndexMap: LinkedHashMap<FiatValue, Int>,
                      private var limitValues: TopUpLimitValues,
                      private var walletBlockedInteract: WalletBlockedInteract,
                      private var inAppPurchaseInteractor: InAppPurchaseInteractor,
                      private var supportInteractor: SupportInteractor) {


  fun getPaymentMethods(value: String, currency: String): Single<List<PaymentMethodData>> {
    return repository.getPaymentMethods(value, currency, "fiat", true)
        .map { mapPaymentMethods(it) }
  }

  fun isWalletBlocked() = walletBlockedInteract.isWalletBlocked()

  fun incrementAndValidateNotificationNeeded(): Single<NotificationNeeded> =
      inAppPurchaseInteractor.incrementAndValidateNotificationNeeded()

  fun showSupport(): Completable {
    return gamificationInteractor.getUserStats()
        .map { it.level }
        .onErrorReturn { 0 }
        .flatMapCompletable { level ->
          inAppPurchaseInteractor.walletAddress
              .flatMapCompletable { wallet ->
                Completable.fromAction {
                  supportInteractor.registerUser(level, wallet.toLowerCase(Locale.ROOT))
                  supportInteractor.displayChatScreen()
                }
              }
        }
  }

  fun convertAppc(value: String): Observable<FiatValue> {
    return conversionService.getAppcToLocalFiat(value, 2)
  }

  fun convertLocal(currency: String, value: String, scale: Int): Observable<FiatValue> {
    return conversionService.getLocalToAppc(currency, value, scale)
  }

  private fun mapPaymentMethods(
      paymentMethods: List<PaymentMethodEntity>): List<PaymentMethodData> {
    // TODO, this should be removed in the release where we include fees on the local payments
    return paymentMethods.filter { Gateway.Name.adyen_v2 == it.gateway.name }
        .map { PaymentMethodData(it.iconUrl, it.label, it.id, !isUnavailable(it)) }
  }

  fun getEarningBonus(packageName: String, amount: BigDecimal): Single<ForecastBonusAndLevel> {
    return gamificationInteractor.getEarningBonus(packageName, amount)
  }

  fun getLimitTopUpValues(): Single<TopUpLimitValues> {
    return if (limitValues.maxValue != TopUpLimitValues.INITIAL_LIMIT_VALUE &&
        limitValues.minValue != TopUpLimitValues.INITIAL_LIMIT_VALUE) {
      Single.just(limitValues)
    } else {
      topUpValuesService.getLimitValues()
          .doOnSuccess { if (!it.error.hasError) cacheLimitValues(it) }
    }
  }

  fun getDefaultValues(): Single<TopUpValuesModel> {
    return if (chipValueIndexMap.isNotEmpty()) {
      Single.just(TopUpValuesModel(ArrayList(chipValueIndexMap.keys)))
    } else {
      topUpValuesService.getDefaultValues()
          .doOnSuccess { if (!it.error.hasError) cacheChipValues(it.values) }
    }
  }

  fun cleanCachedValues() {
    limitValues = TopUpLimitValues()
    chipValueIndexMap.clear()
  }

  fun isBonusValidAndActive(): Boolean {
    return gamificationInteractor.isBonusActiveAndValid()
  }

  fun isBonusValidAndActive(forecastBonusAndLevel: ForecastBonusAndLevel): Boolean {
    return gamificationInteractor.isBonusActiveAndValid(forecastBonusAndLevel)
  }

  private fun cacheChipValues(chipValues: List<FiatValue>) {
    for (index in chipValues.indices) {
      chipValueIndexMap[chipValues[index]] = index
    }
  }

  private fun cacheLimitValues(values: TopUpLimitValues) {
    limitValues = TopUpLimitValues(values.minValue, values.maxValue)
  }

  private fun isUnavailable(paymentMethod: PaymentMethodEntity) =
      paymentMethod.availability == "UNAVAILABLE"
}