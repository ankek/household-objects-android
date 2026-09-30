package dev.hho.android.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.outbox.OutboxOverlay
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemCustomFieldDao
import dev.hho.android.data.room.ItemCustomFieldEntity
import dev.hho.android.data.room.ItemDao
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemIdentificationDao
import dev.hho.android.data.room.ItemIdentificationEntity
import dev.hho.android.data.room.ItemLabelDao
import dev.hho.android.data.room.ItemLabelEntity
import dev.hho.android.data.room.LabelDao
import dev.hho.android.data.room.LabelEntity
import dev.hho.android.data.room.LocationDao
import dev.hho.android.data.room.LocationEntity
import dev.hho.android.data.room.PurchasedFromBlockDao
import dev.hho.android.data.room.PurchasedFromBlockEntity
import dev.hho.android.data.room.SoldToBlockDao
import dev.hho.android.data.room.SoldToBlockEntity
import dev.hho.android.data.room.StockAdjustmentDao
import dev.hho.android.data.room.StockAdjustmentEntity
import dev.hho.android.data.room.WarrantyBlockDao
import dev.hho.android.data.room.WarrantyBlockEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ItemDetailViewModel
    @Inject
    constructor(
        private val itemDao: ItemDao,
        private val itemLabelDao: ItemLabelDao,
        private val labelDao: LabelDao,
        private val locationDao: LocationDao,
        private val warrantyBlockDao: WarrantyBlockDao,
        private val soldToBlockDao: SoldToBlockDao,
        private val purchasedFromBlockDao: PurchasedFromBlockDao,
        private val itemIdentificationDao: ItemIdentificationDao,
        private val itemCustomFieldDao: ItemCustomFieldDao,
        private val stockAdjustmentDao: StockAdjustmentDao,
        private val db: HhoDatabase,
    ) : ViewModel() {

        private val itemIdFlow = MutableStateFlow<String?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<ItemDetailUiState> =
            itemIdFlow
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest(::observeItem)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = ItemDetailUiState.Loading,
                )

        fun loadItem(id: String) {
            itemIdFlow.value = id
        }

        private fun observeItem(id: String): Flow<ItemDetailUiState> {
            val core =
                combine(
                    itemDao.observeById(id),
                    itemLabelDao.observeByItemId(id),
                    labelDao.observeAll(),
                    locationDao.observeAll(),
                    OutboxOverlay.observeDerivedQuantity(db, id),
                ) { item, assignments, labels, locations, derivedQuantity ->
                    CoreDetails(item, assignments, labels, locations, derivedQuantity)
                }

            val blocks =
                combine(
                    warrantyBlockDao.observeByItemId(id),
                    soldToBlockDao.observeByItemId(id),
                    purchasedFromBlockDao.observeByItemId(id),
                ) { warranty, soldTo, purchasedFrom -> BlockDetails(warranty, soldTo, purchasedFrom) }

            val lists =
                combine(
                    itemIdentificationDao.observeByItemId(id),
                    itemCustomFieldDao.observeByItemId(id),
                    stockAdjustmentDao.observeByItemId(id),
                ) { identifiers, customFields, stockAdjustments ->
                    ListDetails(identifiers, customFields, stockAdjustments)
                }

            return combine(core, blocks, lists) { coreDetails, blockDetails, listDetails ->
                toUiState(coreDetails, blockDetails, listDetails)
            }
        }

        private fun toUiState(
            core: CoreDetails,
            blocks: BlockDetails,
            lists: ListDetails,
        ): ItemDetailUiState {
            val item = core.item ?: return ItemDetailUiState.NotFound

            val labelNames = core.assignments.mapNotNull { assignment ->
                core.labels.firstOrNull { it.id == assignment.labelId }?.name
            }
            val locationName = item.locationId?.let { locationId ->
                core.locations.firstOrNull { it.id == locationId }?.name
            }

            return ItemDetailUiState.Data(
                ItemDetailData(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    quantity = core.derivedQuantity?.takeUnless { item.quantity == null && it == 0L },
                    shortCode = item.shortCode,
                    locationName = locationName,
                    labelNames = labelNames,
                    warranty = blocks.warranty?.toData(),
                    soldTo = blocks.soldTo?.toData(),
                    purchasedFrom = blocks.purchasedFrom?.toData(),
                    identifiers = lists.identifiers.map { it.toData() },
                    customFields = lists.customFields.map { it.toData() },
                    stockAdjustments = lists.stockAdjustments.map { it.toData() },
                ),
            )
        }

        private data class CoreDetails(
            val item: ItemEntity?,
            val assignments: List<ItemLabelEntity>,
            val labels: List<LabelEntity>,
            val locations: List<LocationEntity>,
            val derivedQuantity: Long?,
        )

        private data class BlockDetails(
            val warranty: WarrantyBlockEntity?,
            val soldTo: SoldToBlockEntity?,
            val purchasedFrom: PurchasedFromBlockEntity?,
        )

        private data class ListDetails(
            val identifiers: List<ItemIdentificationEntity>,
            val customFields: List<ItemCustomFieldEntity>,
            val stockAdjustments: List<StockAdjustmentEntity>,
        )
    }

private fun WarrantyBlockEntity.toData() =
    WarrantyBlockData(
        isLifetime = isLifetime,
        holder = holder,
        provider = provider,
        startsOn = startsOn,
        expiresOn = expiresOn,
        notes = notes,
    )

private fun SoldToBlockEntity.toData() =
    SoldToBlockData(
        salePriceMinor = salePriceMinor,
        buyerName = buyerName,
        soldOn = soldOn,
        notes = notes,
    )

private fun PurchasedFromBlockEntity.toData() =
    PurchasedFromBlockData(
        purchasePriceMinor = purchasePriceMinor,
        vendor = vendor,
        purchasedOn = purchasedOn,
        orderReference = orderReference,
        notes = notes,
    )

private fun ItemIdentificationEntity.toData() =
    IdentifierData(kind = kind, value = value)

private fun StockAdjustmentEntity.toData() =
    StockAdjustmentData(
        delta = delta,
        resultingQuantity = resultingQuantity,
        reason = reason,
        note = note,
        createdAt = createdAt,
    )

private fun ItemCustomFieldEntity.toData(): CustomFieldData =
    CustomFieldData(
        name = name,
        value = when (fieldType) {
            "text" -> textValue?.let(CustomFieldValue::Text)
            "number" -> numberValue?.let(CustomFieldValue::Number)
            "boolean" -> boolValue?.let(CustomFieldValue::Bool)
            "date" -> dateValue?.let(CustomFieldValue::Date)
            else -> null
        },
    )

private const val STOP_TIMEOUT_MILLIS = 5_000L

sealed interface ItemDetailUiState {
    data object Loading : ItemDetailUiState

    data object NotFound : ItemDetailUiState

    data class Data(val item: ItemDetailData) : ItemDetailUiState
}

data class ItemDetailData(
    val id: String,
    val name: String,
    val description: String?,
    val quantity: Long?,
    val shortCode: String?,
    val locationName: String?,
    val labelNames: List<String>,
    val warranty: WarrantyBlockData?,
    val soldTo: SoldToBlockData?,
    val purchasedFrom: PurchasedFromBlockData?,
    val identifiers: List<IdentifierData>,
    val customFields: List<CustomFieldData>,
    val stockAdjustments: List<StockAdjustmentData>,
)

data class WarrantyBlockData(
    val isLifetime: Boolean,
    val holder: String?,
    val provider: String?,
    val startsOn: LocalDate?,
    val expiresOn: LocalDate?,
    val notes: String?,
)

data class SoldToBlockData(
    val salePriceMinor: Long,
    val buyerName: String?,
    val soldOn: LocalDate?,
    val notes: String?,
)

data class PurchasedFromBlockData(
    val purchasePriceMinor: Long,
    val vendor: String?,
    val purchasedOn: LocalDate?,
    val orderReference: String?,
    val notes: String?,
)

data class IdentifierData(
    val kind: String,
    val value: String,
)

data class CustomFieldData(
    val name: String,
    val value: CustomFieldValue?,
)

sealed interface CustomFieldValue {
    data class Text(val value: String) : CustomFieldValue

    data class Number(val value: BigDecimal) : CustomFieldValue

    data class Bool(val value: Boolean) : CustomFieldValue

    data class Date(val value: LocalDate) : CustomFieldValue
}

data class StockAdjustmentData(
    val delta: Long,
    val resultingQuantity: Long,
    val reason: String?,
    val note: String?,
    val createdAt: Long?,
)
