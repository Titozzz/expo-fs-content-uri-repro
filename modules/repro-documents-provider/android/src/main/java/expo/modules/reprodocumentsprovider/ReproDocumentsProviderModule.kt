package expo.modules.reprodocumentsprovider

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

// Empty module: it only exists so autolinking merges the provider's manifest.
class ReproDocumentsProviderModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ReproDocumentsProvider")
  }
}
