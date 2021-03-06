package com.kevin.rfidmanager.Adapter


import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import at.markushi.ui.CircleButton
import com.daimajia.swipe.SwipeLayout
import com.kevin.rfidmanager.Activity.ItemDetailActivity
import com.kevin.rfidmanager.Activity.ItemEditActivity
import com.kevin.rfidmanager.Activity.ItemInventoryActivity
import com.kevin.rfidmanager.Activity.ItemListActivity
import com.kevin.rfidmanager.MyApplication
import com.kevin.rfidmanager.R
import com.kevin.rfidmanager.Utils.ConstantManager
import com.kevin.rfidmanager.Utils.DatabaseUtil
import com.kevin.rfidmanager.Utils.SPUtil
import com.kevin.rfidmanager.Utils.ScreenUtil
import com.kevin.rfidmanager.database.ImagesPathDao
import com.kevin.rfidmanager.database.Items
import com.kevin.rfidmanager.database.KeyDescription
import com.kevin.rfidmanager.database.KeyDescriptionDao
import com.squareup.picasso.Picasso
import org.jetbrains.anko.onClick
import org.jetbrains.anko.toast
import java.io.File

/**
 * Created by Kevin on 2017/1/29.
 * Mail: chewenkaich@gmail.com
 */

class ItemListAdaper(val activity: Activity, internal var itemes: MutableList<Items>,
                     var recyclerView: RecyclerView? = null, var deleteItemsButton: CircleButton,
                     internal var emptyHint: TextView, internal val isItemListAdapter: Boolean) : RecyclerView.Adapter<ItemListAdaper.ViewHolder>() {
    val circleDialog: ProgressDialog = ProgressDialog(activity.applicationContext)
    var deleteMdoe = false
    val checkedItems: ArrayList<Items> = ArrayList<Items>()
    val itemsIDInCart = ArrayList<String>()
    val loadedImagesPath = ArrayList<String>()
    val context: Context
        get() = activity.applicationContext

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        var contactView = inflater.inflate(R.layout.item_adapter_layout, parent, false)
        // Inflate the custom layout
        if (SPUtil.getInstence(activity).apperance == ConstantManager.DETAIL_LAYOUT)
            contactView = inflater.inflate(R.layout.item_adapter_detail_layout, parent, false)

        // Return a new holder instance
        return ViewHolder(contactView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val theCheckBox: CheckBox = holder.deleteCheckBox
        if (deleteMdoe) {
            theCheckBox.visibility = View.VISIBLE
            deleteItemsButton.visibility = View.VISIBLE
        } else {
            theCheckBox.visibility = View.GONE
            deleteItemsButton.visibility = View.GONE
        }
        val swipeLayout = holder.swipeLayout
        //set show mode.
        swipeLayout.showMode = SwipeLayout.ShowMode.LayDown
        swipeLayout.isRightSwipeEnabled = false

        //add drag edge.(If the BottomView has 'layout_gravity' attribute, this line is unnecessary)
        if (isItemListAdapter)
            swipeLayout.addDrag(SwipeLayout.DragEdge.Left, holder.itemView.findViewById(R.id.bottom_wrapper))

        // Get the data model based on position
        val item = itemes[position]

        theCheckBox.setOnClickListener {
            if (!theCheckBox.isChecked and (item in checkedItems)) {
                checkedItems.remove(item)
            }
            if (theCheckBox.isChecked and !(item in checkedItems)) {
                checkedItems.add(item)
            }
        }

        // Set item views based on your views and data model
        val image = holder.image
        if (item.mainImagePath == null) {
            Picasso.with(activity).load(R.drawable.image_read_fail).resize(ScreenUtil.getScreenWidth(activity), 0).into(image)
        } else {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                if (File(item.mainImagePath).exists()) {
                    // Do not reload image if it already shown
                    if (holder.image.tag != item.mainImagePath) {
                        Picasso.with(activity).load(File(item.mainImagePath)).resize(ScreenUtil.getScreenWidth(activity), 0).placeholder(R.drawable.loading_image)
                                .into(image)
                        holder.image.tag = item.mainImagePath
                        loadedImagesPath.add(item.mainImagePath)
                    }
                } else {
                    Picasso.with(activity).load(R.drawable.image_read_fail).resize(ScreenUtil.getScreenWidth(activity), 0).into(image)
                }
            } else {
                Picasso.with(activity).load(R.drawable.image_read_fail).resize(ScreenUtil.getScreenWidth(activity), 0).into(image)
            }
        }

        image.setOnClickListener {
            val intent = Intent(activity, ItemDetailActivity::class.java)
            intent.putExtra(ConstantManager.CURRENT_ITEM_ID, item.rfid)
            activity.startActivity(intent)
        }

        if (true) { //isItemListAdapter
            val longClickListener = { v: View ->
                deleteMdoe = !deleteMdoe
                if (deleteMdoe) {
                    setCachedCheckBoxVisible()
                    theCheckBox.isChecked = true
                    checkedItems.add(item)
                    deleteItemsButton.visibility = View.VISIBLE
                } else {
                    setCachedCheckBoxGone()
                    deleteItemsButton.visibility = View.GONE
                }
                true
            }
            image.setOnLongClickListener(longClickListener)
        }

        holder.editItem.setOnClickListener {
            //                ((MainActivity)activity).viewPager.setCurrentItem(ConstantManager.EDIT, false);
            //                ((MainActivity)activity).adapter.tab3.refreshUI();
            val intent = Intent(activity, ItemEditActivity::class.java)
            intent.putExtra(ConstantManager.CURRENT_ITEM_ID, item.rfid)
            activity.startActivity(intent)
        }
        holder.deleteItem.setOnClickListener { deleteItemDialog(item) }

        val itemName = holder.itemName
        itemName.text = item.itemName

        val keys = DatabaseUtil.queryItemsKeyDes(activity, item.rfid)
        var keyText: StringBuffer = StringBuffer()
        for (key: KeyDescription in keys) {
            keyText.append(" * " + key.keyDescription + "\n")
        }
        holder.keyDes.hint = context.getString(R.string.no_key_description_information)
        holder.keyDes.text = keyText
        when (SPUtil.getInstence(activity).apperance) {
            ConstantManager.LINEAR_LAYOUT, ConstantManager.STAGGER_LAYOUT, ConstantManager.ONE_ROW_LAYOUT  // ConstantManager.LINEAR_LAYOUT
            -> {
                holder.keyDes.visibility = View.GONE
            }
            ConstantManager.DETAIL_LAYOUT -> {
                holder.keyDes.visibility = View.VISIBLE
            }
        }

        if (ItemListActivity::class.java.isInstance(activity))
            holder.price.text = "$" + (item.price.toInt()).toString() + " Stock:" + (item.avaliableInventory.toString())
        else if (ItemInventoryActivity::class.java.isInstance(activity))
            holder.price.text = "$" + (item.price.toInt()).toString()

        // Add To Cart part
        if (isItemListAdapter) {
            holder.addToCart.visibility = View.GONE
        } else {
            holder.addToCart.visibility = View.VISIBLE

            if (itemsIDInCart.contains(item.rfid))
                holder.addToCart.setImageResource(R.drawable.remove_shopping)
            else
                holder.addToCart.setImageResource(R.drawable.add_shopping)

            holder.addToCart.onClick {
                if (itemsIDInCart.contains(item.rfid)) {
                    itemsIDInCart.remove(item.rfid)
                    holder.addToCart.setImageResource(R.drawable.add_shopping)
                } else {
                    // check the stock
                    if (item.avaliableInventory > 0) {
                        itemsIDInCart.add(item.rfid)
                        activity.toast("Added to cart.")
                        holder.addToCart.setImageResource(R.drawable.remove_shopping)
                    } else {
                        activity.toast("Out of stock")
                        return@onClick
                    }
                }
            }
        }

    }

    override fun getItemCount(): Int {
        if (itemes.size == 0)
            emptyHint.visibility = View.VISIBLE
        else
            emptyHint.visibility = View.GONE
        return itemes.size
    }

    override fun getItemId(position: Int): Long {
        return itemes.get(position).id
    }

    fun setCachedCheckBoxVisible() {
        for (i: Int in 0..this.itemCount - 1) {
            val defaultViewHolder = this.recyclerView!!.findViewHolderForAdapterPosition(i)
            if (defaultViewHolder != null) {
                (defaultViewHolder as ViewHolder).deleteCheckBox.visibility = View.VISIBLE
            }
        }
        deleteItemsButton.visibility = View.VISIBLE
    }

    fun setCachedCheckBoxGone() {
        for (i: Int in 0..this.itemCount - 1) {
            val defaultViewHolder = this.recyclerView!!.findViewHolderForAdapterPosition(i)
            if (defaultViewHolder != null) {
                (defaultViewHolder as ViewHolder).deleteCheckBox.visibility = View.GONE
                (defaultViewHolder as ViewHolder).deleteCheckBox.isChecked = false
            }
        }
        deleteItemsButton.visibility = View.GONE
        checkedItems.removeAll(checkedItems)
    }

    fun deleteSelectedItems() {
        circleDialog.setTitle("Deleting...")
        circleDialog.setMessage("please wait a while")
        circleDialog.show()
        Thread().run {
            val daoSession = (activity.application as MyApplication).getmDaoSession()
            for (i: Int in 0..checkedItems.size - 1) {
                daoSession.itemsDao.deleteInTx(checkedItems.get(i))
                // delete image path
                daoSession.imagesPathDao.deleteInTx(daoSession.imagesPathDao.queryBuilder().where(ImagesPathDao.Properties.Rfid.eq(checkedItems.get(i).rfid)).build().list())
                // delete key description
                daoSession.keyDescriptionDao.deleteInTx(daoSession.keyDescriptionDao.queryBuilder().where(KeyDescriptionDao.Properties.Rfid.eq(checkedItems.get(i).rfid)).build().list())
            }
            circleDialog.dismiss()
            deleteItemsButton.visibility = View.GONE
            deleteMdoe = false
            updateUI()
            Toast.makeText(activity, R.string.delete_success, Toast.LENGTH_LONG).show()
        }

    }

    /**
     * This method used to refresh list data.
     * We need to query all item in var itemes, make sure all of it is exist in database
     */
    fun updateUI() {
        this.itemes.clear()
        // Sorts the items
        val items = DatabaseUtil.queryItems(activity, (activity as ItemListActivity).currentUser)
        items.sortBy { it.itemName }
        this.itemes.addAll(items)
        this.notifyDataSetChanged()
        getItemCount()
    }


    /**
     * Set the new Items as data and notify list to refresh.
     */
    fun updateUI(items: ArrayList<Items>) {
//        var itemesIter: MutableIterator<Items> = itemes.iterator();
//        while (itemesIter.hasNext()) {
//            var originItem = itemesIter.next()
//
//            if (!items.contains(originItem)) {
//                val index = itemes.indexOf(originItem)
//                itemesIter.remove()
//                notifyItemRemoved(index)
//            }
//        }
//
//        for (item in items) {
//            if (!itemes.contains(item)) {
//                itemes.add(item)
//                notifyDataSetChanged()
//            }
//        }
        this.itemes.removeAll(this.itemes)
        items.sortBy { it.itemName }
        this.itemes.addAll(items)
        this.notifyDataSetChanged()
        getItemCount()
    }

    /**
     * When card reader find a new card which has been saved in the database.
     * Then add it in the item list.
     */
    fun addNewItemToList(item: Items) {
        val isContain = itemes.filter { it.rfid==item.rfid }.size!=0
        if (isContain)
            return
        else {
            itemes.add(item)
            itemes.sortBy { it.itemName }
            this.notifyDataSetChanged()
        }
    }

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Your holder should contain a member variable
        // for any view that will be set as you render a row
        var swipeLayout: SwipeLayout = itemView.findViewById(R.id.swipe_layout) as SwipeLayout
        var image: ImageView = itemView.findViewById(R.id.item_thumb) as ImageView
        var itemName: TextView = itemView.findViewById(R.id.list_item_name) as TextView
        var editItem: CircleButton = itemView.findViewById(R.id.edit_item) as CircleButton
        var deleteItem: CircleButton = itemView.findViewById(R.id.remove_item) as CircleButton
        var deleteCheckBox: CheckBox = itemView.findViewById(R.id.item_delete_check_box) as CheckBox
        var keyDes: TextView = itemView.findViewById(R.id.itemlist_key_des) as TextView
        var price: TextView = itemView.findViewById(R.id.et_price) as TextView
        var addToCart: ImageView = itemView.findViewById(R.id.add_to_cart) as ImageView

        // to access the context from any ViewHolder instance.
    }

    /*
    This is a dialog used for delete item
     */
    fun deleteItemDialog(item: Items) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.delete_confirm_title)
        builder.setMessage(R.string.delete_confirm)
        builder.setPositiveButton(R.string.OK) { dialog, which ->
            val daoSession = (activity.application as MyApplication).getmDaoSession()
            daoSession.itemsDao.delete(item)
            // delete image path
            daoSession.imagesPathDao.deleteInTx(daoSession.imagesPathDao.queryBuilder().where(ImagesPathDao.Properties.Rfid.eq(item.rfid)).build().list())
            // delete key description
            daoSession.keyDescriptionDao.deleteInTx(daoSession.keyDescriptionDao.queryBuilder().where(KeyDescriptionDao.Properties.Rfid.eq(item.rfid)).build().list())
            Toast.makeText(activity, R.string.delete_success, Toast.LENGTH_LONG).show()
            updateUI()
        }
        builder.setNegativeButton(R.string.Cancel) { dialog, which -> }
        builder.create().show()
    }
}