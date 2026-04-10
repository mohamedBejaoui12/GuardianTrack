package com.guardian.track.ui.history

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.guardian.track.databinding.FragmentHistoryBinding
import com.guardian.track.databinding.ItemIncidentBinding
import com.guardian.track.model.Incident
import com.guardian.track.repository.IncidentRepository
import com.guardian.track.util.CsvExporter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    /**
     * stateIn converts a cold Flow into a hot StateFlow.
     * SharingStarted.WhileSubscribed(5000): keeps the upstream Flow active for 5s
     * after the last subscriber disappears (e.g. during rotation) to avoid
     * restarting the Room query on every rotation.
     */
    val incidents = incidentRepository.getAllIncidents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteIncident(id: Long) {
        viewModelScope.launch { incidentRepository.deleteIncident(id) }
    }

    suspend fun getAllForExport() = incidentRepository.getAllForExport()
}

// ─────────────────────────────────────────────
//  DiffUtil Callback — efficient list updates
// ─────────────────────────────────────────────

/**
 * DiffUtil calculates the minimal set of changes needed to update the RecyclerView.
 * Without it, notifyDataSetChanged() would rebind every row on every update.
 * With it, only changed rows are animated/rebound.
 */
class IncidentDiffCallback : DiffUtil.ItemCallback<Incident>() {
    override fun areItemsTheSame(old: Incident, new: Incident) = old.id == new.id
    override fun areContentsTheSame(old: Incident, new: Incident) = old == new
}

// ─────────────────────────────────────────────
//  RecyclerView Adapter
// ─────────────────────────────────────────────

class IncidentAdapter(
    private val onDelete: (Long) -> Unit
) : ListAdapter<Incident, IncidentAdapter.ViewHolder>(IncidentDiffCallback()) {

    inner class ViewHolder(private val binding: ItemIncidentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(incident: Incident) {
            binding.tvDate.text = "${incident.formattedDate} ${incident.formattedTime}"
            binding.tvType.text = incident.type
            binding.tvLocation.text = if (incident.latitude == 0.0 && incident.longitude == 0.0)
                "Location unavailable"
            else
                "%.4f, %.4f".format(incident.latitude, incident.longitude)
            binding.tvSynced.text = if (incident.isSynced) "✓ Synced" else "⏳ Pending"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIncidentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}

// ─────────────────────────────────────────────
//  Fragment
// ─────────────────────────────────────────────

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private val viewModel: HistoryViewModel by viewModels()
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: IncidentAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = IncidentAdapter(onDelete = { id -> viewModel.deleteIncident(id) })

        binding.recyclerView.apply {
            this.adapter = this@HistoryFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())

            // Swipe-to-delete
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                override fun onMove(
                    rv: RecyclerView,
                    vh: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ) = false
                override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                    val position = vh.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return
                    val incident = this@HistoryFragment.adapter.currentList[position]
                    viewModel.deleteIncident(incident.id)
                }


            }).attachToRecyclerView(binding.recyclerView)

        }

        // Observe the StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.incidents.collect { incidents ->
                    adapter.submitList(incidents)
                    binding.tvEmpty.visibility = if (incidents.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        binding.btnExport.setOnClickListener { exportToCsv() }
    }

    private fun exportToCsv() {
        lifecycleScope.launch {
            val incidents = viewModel.getAllForExport()
            val success = CsvExporter.export(requireContext(), incidents)
            Toast.makeText(
                requireContext(),
                if (success) "Exported to Documents/" else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
