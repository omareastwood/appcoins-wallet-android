package com.asfoundation.wallet.di

import com.asfoundation.wallet.ui.gamification.MyLevelFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector


@Module
interface RewardsLevelModule {
  @FragmentScope
  @ContributesAndroidInjector(modules = arrayOf(MyLevelFragmentModule::class))
  fun myLevelFragment(): MyLevelFragment
}