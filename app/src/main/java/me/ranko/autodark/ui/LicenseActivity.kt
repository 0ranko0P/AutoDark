package me.ranko.autodark.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ranko.autodark.R
import me.ranko.autodark.databinding.ItemLicenseBinding
import me.ranko.autodark.databinding.LicenseActivityBinding
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Activity to show licenses
 *
 * @author  0ranko0P
 * */
class LicenseActivity : AppCompatActivity(), LicenseAdapter.LicenseClickListener {

    val mLicenseList: ObservableArrayList<License> = ObservableArrayList()

    private lateinit var adapter: LicenseAdapter

    private lateinit var binding: LicenseActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.license_activity)
        binding.lifecycleOwner = this
        adapter = LicenseAdapter(this)

        lifecycleScope.launch {
            readLicense(mLicenseList)
            binding.recyclerView.adapter = adapter
            adapter.setData(mLicenseList)
        }
    }

    private suspend fun readLicense(list: MutableList<License>) = withContext(Dispatchers.IO) {
        val apache2 = getString(R.string.license_apache_2_0)
        val apache2Url = getString(R.string.license_apache_2_0_url)
        try {
            BufferedReader(InputStreamReader(assets.open("License.txt"), StandardCharsets.UTF_8)).useLines { lines ->
                lines.forEach {
                    val json = JSONObject(it)
                    list.add(
                        License(
                            json.getString("n"),
                            nonEmpty(json.getString("li"), apache2),
                            nonEmpty(json.getString("url"), apache2Url)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.d(e)
        }

        return@withContext list
    }

    private fun nonEmpty(str: String, default: String): String = if (str.isEmpty()) default else str

    override fun onClicked(license: License) {
        startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(license.url)))
    }
}

data class License(
    val name: String,
    val license: String,
    val url: String
)

class LicenseAdapter(private val listener: LicenseClickListener) :
    RecyclerView.Adapter<LicenseAdapter.ViewHolder>(){

    private var mList: List<License>? = null

    interface LicenseClickListener {
        fun onClicked(license: License)
    }

    companion object class ViewHolder(
        val binding: ItemLicenseBinding,
        private val listener: LicenseClickListener
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {
        var license: License? = null

        override fun onClick(v: View) {
            listener.onClicked(license!!)
        }
    }

    fun setData(list: List<License>) {
        this.mList = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemLicenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            , listener
        )
    }

    override fun getItemCount(): Int = mList?.size ?: 0

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val license = mList!![position]
        holder.license = license
        holder.binding.license = license
        holder.binding.root.setOnClickListener(holder)
    }
}