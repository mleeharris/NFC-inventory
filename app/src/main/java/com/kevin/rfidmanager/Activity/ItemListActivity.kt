package com.kevin.rfidmanager.Activity

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.media.SoundPool
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.design.widget.TextInputEditText
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.*
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.*
import at.markushi.ui.CircleButton
import com.github.mjdev.libaums.UsbMassStorageDevice
import com.github.mjdev.libaums.fs.FileSystem
import com.github.mjdev.libaums.fs.UsbFile
import com.github.mjdev.libaums.fs.UsbFileOutputStream
import com.github.mjdev.libaums.fs.UsbFileStreamFactory
import com.kevin.rfidmanager.Adapter.ItemListAdaper
import com.kevin.rfidmanager.Adapter.NewCardListAdapter
import com.kevin.rfidmanager.Adapter.StorageDevicesAdaper
import com.kevin.rfidmanager.MyApplication
import com.kevin.rfidmanager.R
import com.kevin.rfidmanager.Utils.*
import com.kevin.rfidmanager.database.ItemsDao
import com.nightonke.boommenu.BoomButtons.ButtonPlaceEnum
import com.nightonke.boommenu.BoomButtons.HamButton
import com.nightonke.boommenu.BoomMenuButton
import com.nightonke.boommenu.ButtonEnum
import com.nightonke.boommenu.Piece.PiecePlaceEnum
import com.rfid.api.ADReaderInterface
import com.rfid.api.GFunction
import com.rfid.api.ISO15693Interface
import com.rfid.api.ISO15693Tag
import com.rfid.def.ApiErrDefinition
import com.rfid.def.RfidDef
import kotlinx.android.synthetic.main.item_list_layout.*
import org.jetbrains.anko.onClick
import org.jetbrains.anko.toast
import java.io.*
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList

/**
 * Main page of the app
 */
class ItemListActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var itemListAdapter: ItemListAdaper? = null
    private var storageDevicesAdaper: StorageDevicesAdaper? = null
    private var mNfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var deleteItemsButton: CircleButton? = null
    var currentUser = ConstantManager.DEFAULT_USER
    var currentID = ConstantManager.DEFAULT_RFID
    // Read RFID PART
    private var m_reader: ADReaderInterface = ADReaderInterface()
    private var m_inventoryThrd: Thread? = null// The thread of inventory
    private var m_getScanRecordThrd: Thread? = null// The thead of scanf the record.

    val INVENTORY_MSG = 1
    val GETSCANRECORD = 2
    val INVENTORY_FAIL_MSG = 4
    val THREAD_END = 3
    private var soundPool: SoundPool? = null
    private var soundID = 0
    var newCardsIDsStringList = ArrayList<String>()
    var dataAdapter: NewCardListAdapter? = null
    var alertDialog: AlertDialog? = null
    // Inventory parameter
    internal var INVENTORY_REQUEST_CODE = 1// requestCode
    private var bUseDefaultPara = true
    private var bOnlyReadNew = false
    private var bMathAFI = false
    private var mAFIVal: Byte = 0x00
    private var bBuzzer = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.item_list_layout)
        initActionBar()
        initUI()
        initAddItemDialog()
        initNFC()
        startScan()
    }

    private fun initActionBar() {
        val mActionBar = supportActionBar!!
        mActionBar.setDisplayShowHomeEnabled(false)
        mActionBar.setDisplayShowTitleEnabled(false)
        val mInflater = LayoutInflater.from(this)

        val actionBar = mInflater.inflate(R.layout.custom_action_bar, null)
        val mTitleTextView = actionBar.findViewById(R.id.title_text) as TextView
        mTitleTextView.setText(R.string.app_name)
        mTitleTextView.setTextColor(resources.getColor(R.color.black))
        mActionBar.customView = actionBar
        mActionBar.setDisplayShowCustomEnabled(true)
        (actionBar.parent as Toolbar).setContentInsetsAbsolute(0, 0)

        val paddingPixels = ScreenUtil.dpToPx(this, 5)
        val leftBmb = actionBar.findViewById(R.id.action_bar_left_bmb) as BoomMenuButton

        leftBmb.buttonEnum = ButtonEnum.Ham
        leftBmb.piecePlaceEnum = PiecePlaceEnum.HAM_6
        leftBmb.buttonPlaceEnum = ButtonPlaceEnum.HAM_6

        val changeAppearance = HamButton.Builder()
                .listener { changeApperanceDialog() }
                .normalImageRes(R.drawable.ic_color_lens_white_48dp)
                .imagePadding(Rect(paddingPixels, paddingPixels, paddingPixels, paddingPixels))
                .normalTextRes(R.string.change_apperance)
                .containsSubText(false)
        leftBmb.addBuilder(changeAppearance)

        val backup = HamButton.Builder()
                .listener { backupDialog() }
                .normalImageRes(R.drawable.ic_settings_backup_restore_white_48dp)
                .imagePadding(Rect(paddingPixels, paddingPixels, paddingPixels, paddingPixels))
                .normalTextRes(R.string.backup_database)
                .containsSubText(false)
        leftBmb.addBuilder(backup)

        val restore = HamButton.Builder()
                .listener { restoreDialog() }
                .normalImageRes(R.drawable.ic_restore_white_48dp)
                .imagePadding(Rect(paddingPixels, paddingPixels, paddingPixels, paddingPixels))
                .normalTextRes(R.string.restore_database)
                .containsSubText(false)
        leftBmb.addBuilder(restore)

        val changePassword = HamButton.Builder()
                .listener { showPasswordChangeDialog() }
                .normalImageRes(R.drawable.key)
                .imagePadding(Rect(paddingPixels, paddingPixels, paddingPixels, paddingPixels))
                .normalTextRes(R.string.change_password)
                .containsSubText(false)
        leftBmb.addBuilder(changePassword)

        val change_rfid_range = HamButton.Builder()
                .listener { showPasswordChangeDialog(); }
                .normalImageRes(R.drawable.range)
                .imagePadding(Rect(paddingPixels, paddingPixels, paddingPixels, paddingPixels))
                .normalTextRes(R.string.change_rfid_range)
        leftBmb.addBuilder(change_rfid_range)


        val log_out = HamButton.Builder()
                .listener {
                    SPUtil.getInstence(applicationContext).saveNeedPassword(true)
                    startActivity(Intent(this@ItemListActivity, LoginActivity::class.java))
                    currentID = ConstantManager.DEFAULT_RFID
                    finish()
                }
                .normalImageRes(R.drawable.logout)
                .imagePadding(Rect(paddingPixels, paddingPixels, paddingPixels, paddingPixels))
                .normalTextRes(R.string.log_out)
        leftBmb.addBuilder(log_out)

        //        rightBmb.setButtonEnum(ButtonEnum.Ham);
        //        rightBmb.setPiecePlaceEnum(PiecePlaceEnum.HAM_4);
        //        rightBmb.setButtonPlaceEnum(ButtonPlaceEnum.HAM_4);
        //        for (int i = 0; i < rightBmb.getPiecePlaceEnum().pieceNumber(); i++)
        //            rightBmb.addBuilder(BuilderManager.getHamButtonBuilder());
    }

    private fun initUI() {
        currentUser = intent.getStringExtra(ConstantManager.CURRENT_USER_NAME)
        recyclerView = findViewById(R.id.recycle_item_list) as RecyclerView
        val items = DatabaseUtil.queryItems(this@ItemListActivity, currentUser)
        deleteItemsButton = delete_items_button
        itemListAdapter = ItemListAdaper(this@ItemListActivity, items, recyclerView, deleteItemsButton!!)
        recyclerView!!.adapter = itemListAdapter
        setRecyclerViewLayout()
        recyclerView!!.setHasFixedSize(true)
        registUSBBroadCast()
        deleteItemsButton!!.setOnClickListener { itemListAdapter!!.deleteSelectedItems() }
        // 初始化声音
        // Initialize the sound
        soundPool = SoundPool(5, AudioManager.STREAM_MUSIC, 5)
        soundID = soundPool!!.load(this, R.raw.msg, 1)

    }

    private fun registUSBBroadCast() {
        val filter = IntentFilter(ConstantManager.ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)
    }

    private fun initNFC(): Boolean {
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (mNfcAdapter == null) {
            //nfc not support your device.
            return false
        }
        pendingIntent = PendingIntent.getActivity(
                this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)
        return true
    }

    public override fun onResume() {
        super.onResume()
        if (itemListAdapter != null) {
            itemListAdapter!!.updateUI()
        }
        if (mNfcAdapter != null)
            mNfcAdapter!!.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        if (mNfcAdapter != null) {
            mNfcAdapter!!.disableForegroundDispatch(this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        if (intent != null && NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            val ID = HexConvertUtil.bytesToHexString(tag.id)

            // Are there any user info?
            val daoSession = (application as MyApplication).getDaoSession()
            val itemsDao = daoSession.itemsDao
            val items = itemsDao.queryBuilder().where(ItemsDao.Properties.Rfid.like(ID)).build().list()
            if (items.size > 1) {
                Toast.makeText(this@ItemListActivity,
                        R.string.one_ID_multi_items_warning, Toast.LENGTH_LONG).show()
                return
            } else if (items.size == 1) {  // Database have an item bind with this card
                if (items[0].userName == currentUser) {
                    val intentToDetail = Intent(this@ItemListActivity, ItemDetailActivity::class.java)
                    intentToDetail.putExtra(ConstantManager.CURRENT_ITEM_ID, ID)
                    startActivity(intentToDetail)
                } else {
                    Toast.makeText(this@ItemListActivity,
                            R.string.another_users_card, Toast.LENGTH_LONG).show()
                    return
                }
            } else {
                addNewCardToList(ID)
            }

            //            Parcelable[] rawMessages =
            //                    intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            //            if (rawMessages != null) {
            //                NdefMessage[] messages = new NdefMessage[rawMessages.length];
            //                for (int i = 0; i < rawMessages.length; i++) {
            //                    messages[i] = (NdefMessage) rawMessages[i];
            //                }
            //                Toast.makeText(ItemListActivity.this, messages.toString(), Toast.LENGTH_LONG).show();
            //
            //            }
        }
        super.onNewIntent(intent)
    }

    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        stopScan()
        super.onDestroy()
    }

    private fun setRecyclerViewLayout() {
        when (SPUtil.getInstence(this@ItemListActivity).apperance) {
            8  // ConstantManager.LINEAR_LAYOUT
            -> {
                val gridLayoutManager = GridLayoutManager(this@ItemListActivity,
                        3, GridLayoutManager.VERTICAL, false)
                recyclerView!!.layoutManager = gridLayoutManager// Attach the layout manager to
            }
            9  // ConstantManager.STAGGER_LAYOUT
            -> {
                // First param is number of columns and second param is orientation i.e
                // Vertical or Horizontal
                val staggeredGridLayoutManager = StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
                recyclerView!!.layoutManager = staggeredGridLayoutManager
            }
            10  // ConstantManager.ONE_ROW_LAYOUT
            -> {
                val linearLayoutManager = LinearLayoutManager(
                        this@ItemListActivity, LinearLayoutManager.VERTICAL, false)
                recyclerView!!.layoutManager = linearLayoutManager
            }
            11 -> {
                val linearLayoutManager = LinearLayoutManager(
                        this@ItemListActivity, LinearLayoutManager.VERTICAL, false)
                recyclerView!!.layoutManager = linearLayoutManager
            }
        }// the recycler view
    }

    /**
    This is a dialog used for add new key item
     */
    fun initAddItemDialog() {
        val dialogBuilder = AlertDialog.Builder(this@ItemListActivity)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_layout_two_edit_text, null)
        dialogBuilder.setView(dialogView)

        val itemID = dialogView.findViewById(R.id.edit_key_des_text_editor) as TextInputEditText
        val itemName = dialogView.findViewById(R.id.item_name_edit) as TextInputEditText
        val saveButton = dialogView.findViewById(R.id.dialog_change) as Button
        val cancleButton = dialogView.findViewById(R.id.dialog_cancle) as Button
        val cardsList = dialogView.findViewById(R.id.new_cards_list) as ListView
        dataAdapter = NewCardListAdapter(this@ItemListActivity, newCardsIDsStringList, itemID)
        cardsList.adapter = dataAdapter

        dialogBuilder.setTitle("Add new item: ")
        dialogBuilder.setMessage("Take your RFID card close to card reader, the id will appear in the list.")
        alertDialog = dialogBuilder.create()

        saveButton.setOnClickListener(View.OnClickListener {
            val new_id = itemID.text.toString()
            if (new_id.isEmpty()) {
                Toast.makeText(this@ItemListActivity,
                        "please input an ID or close your card to reader.",
                        Toast.LENGTH_LONG).show()
                return@OnClickListener
            }
            // Are there any user info?
            val daoSession = (application as MyApplication).getDaoSession()
            val itemsDao = daoSession.itemsDao
            val items = itemsDao.queryBuilder().where(ItemsDao.Properties.Rfid.like(new_id)).build().list()
            if (items.size > 0) {
                Toast.makeText(this@ItemListActivity,
                        "The ID card is exist, please change a ID", Toast.LENGTH_LONG).show()
                return@OnClickListener
            }
            DatabaseUtil.insertNewItem(this@ItemListActivity,
                    itemID.text.toString(),
                    itemName.text.toString(), currentUser)
            val intent = Intent(this@ItemListActivity, ItemEditActivity::class.java)
            intent.putExtra(ConstantManager.CURRENT_ITEM_ID, itemID.text.toString())
            startActivity(intent)
            alertDialog!!.dismiss()

        })

        cancleButton.setOnClickListener {
            alertDialog!!.dismiss()
        }
    }

    /**
     * Add new card to card ID list, then refresh the list
     */
    fun addNewCardToList(cardID: String) {
        if (alertDialog == null)
            return
        if (dataAdapter == null)
            return
        // Are there any user info?
        val daoSession = (application as MyApplication).getDaoSession()
        val itemsDao = daoSession.itemsDao
        val items = itemsDao.queryBuilder().where(ItemsDao.Properties.Rfid.like(cardID)).build().list()
        if (items.size > 1) {
            Toast.makeText(this@ItemListActivity,
                    R.string.one_ID_multi_items_warning, Toast.LENGTH_LONG).show()
            return
        } else if (items.size == 1) {  // Database have an item bind with this card
            if (items[0].userName == currentUser) {
                val intentToDetail = Intent(this@ItemListActivity, ItemDetailActivity::class.java)
                intentToDetail.putExtra(ConstantManager.CURRENT_ITEM_ID, cardID)
                startActivity(intentToDetail)
            } else {
                Toast.makeText(this@ItemListActivity,
                        R.string.another_users_card, Toast.LENGTH_LONG).show()
                return
            }
        } else {
            dataAdapter!!.addNewCardToList(cardID)
            if (!alertDialog!!.isShowing)
                alertDialog!!.show()
        }
    }

    private fun changeApperanceDialog() {
        val dialogBuilder = AlertDialog.Builder(this@ItemListActivity)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_change_apperance_layout, null)
        dialogBuilder.setView(dialogView)
        dialogBuilder.setTitle(R.string.select_an_appearance)
        dialogBuilder.setNegativeButton(R.string.Cancel) { dialog, which -> }
        val b = dialogBuilder.create()
        val textView = dialogView.findViewById(R.id.backup_dialog_message) as TextView
        val linear_layout = dialogView.findViewById(R.id.linear_layout) as CircleButton
        val staggered_layout = dialogView.findViewById(R.id.staggered_layout) as CircleButton
        val one_row_layout = dialogView.findViewById(R.id.one_row_layout) as CircleButton
        val detail_layout = dialogView.findViewById(R.id.detail_layout) as CircleButton

        when (SPUtil.getInstence(this@ItemListActivity).apperance) {
            8  // ConstantManager.LINEAR_LAYOUT
            -> textView.setText(R.string.current_selection_line)
            9  // ConstantManager.STAGGER_LAYOUT
            -> textView.setText(R.string.current_selection_staggered)
            10  // ConstantManager.ONE_ROW_LAYOUT
            -> textView.setText(R.string.current_selection_one_row)
            11 // ConstantManager.DETAIL_LAYOUT
            -> textView.setText(R.string.current_selection_detail_layout)
        }

        linear_layout.setOnClickListener {
            SPUtil.getInstence(this@ItemListActivity).apperance = ConstantManager.LINEAR_LAYOUT
            (application as MyApplication).toast(getString(R.string.apperance_updated))
            initUI()
            b.dismiss()
        }

        staggered_layout.setOnClickListener {
            SPUtil.getInstence(this@ItemListActivity).apperance = ConstantManager.STAGGER_LAYOUT
            (application as MyApplication).toast(getString(R.string.apperance_updated))
            initUI()
            b.dismiss()
        }

        one_row_layout.setOnClickListener {
            SPUtil.getInstence(this@ItemListActivity).apperance = ConstantManager.ONE_ROW_LAYOUT
            (application as MyApplication).toast(getString(R.string.apperance_updated))
            initUI()
            b.dismiss()
        }

        detail_layout.onClick {
            SPUtil.getInstence(this@ItemListActivity).apperance = ConstantManager.DETAIL_LAYOUT
            (application as MyApplication).toast(getString(R.string.apperance_updated))
            initUI()
            b.dismiss()
        }

        b.show()
    }

    /*
    This is a dialog used for changing password.
     */
    fun showPasswordChangeDialog() {
        val dialogBuilder = AlertDialog.Builder(this@ItemListActivity)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.password_change_dialog_layout, null)
        dialogBuilder.setView(dialogView)

        val oldPasswordEdt = dialogView.findViewById(R.id.old_password_editor) as EditText
        val newPasswordEdt = dialogView.findViewById(R.id.new_password_editor) as EditText
        val confirmNewPasswordEdt = dialogView.findViewById(R.id.confirm_new_password) as EditText
        val message = dialogView.findViewById(R.id.message_text_login) as TextView
        val saveButton = dialogView.findViewById(R.id.dialog_change) as Button
        val cancleButton = dialogView.findViewById(R.id.dialog_cancle) as Button

        dialogBuilder.setTitle(resources.getString(R.string.change_passwd))
        val b = dialogBuilder.create()
        b.show()

        saveButton.setOnClickListener(View.OnClickListener {
            val daoSession = (application as MyApplication).getDaoSession()
            val usersDao = daoSession.usersDao


            val users = DatabaseUtil.queryUsers(this@ItemListActivity,
                    currentUser)
            if (users.size > 1) {
                (application as MyApplication).toast(getString(R.string.illegal_user))
                usersDao.deleteInTx(users)
                return@OnClickListener
            } else {
                val user = users[0]
                // check current password
                if (user.passWord != oldPasswordEdt.text.toString()) {
                    message.setText(R.string.wrong_old_password)
                    message.setTextColor(resources.getColor(R.color.warning_color))
                    return@OnClickListener
                }
                // check password of two text editors
                if (newPasswordEdt.text.toString() != confirmNewPasswordEdt.text.toString()) {
                    message.setText(R.string.diff_passwd)
                    message.setTextColor(resources.getColor(R.color.warning_color))
                    return@OnClickListener
                }
                //save password with edt.getText().toString();

                user.passWord = newPasswordEdt.text.toString()
                usersDao.insertOrReplace(user)
            }

            Toast.makeText(applicationContext,
                    R.string.password_updated, Toast.LENGTH_LONG).show()
            b.dismiss()
        })

        cancleButton.setOnClickListener {
            //dismiss dialog
            b.dismiss()
        }
    }


    /*
    This is a dialog used for backup database
     */
    fun backupDialog() {

        val dialogBuilder = AlertDialog.Builder(this@ItemListActivity)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_backup_layout, null)
        dialogBuilder.setView(dialogView)
        dialogBuilder.setTitle(R.string.select_backup_position)
        dialogBuilder.setNegativeButton(R.string.Cancel) { dialog, which -> }
        val b: AlertDialog

        val textView = dialogView.findViewById(R.id.backup_dialog_message) as TextView
        val recyclerView = dialogView.findViewById(R.id.recycle_view_storage_devices_list) as RecyclerView
        val deviceFiles = USBUtil.getDevicePathSet(this@ItemListActivity)
        if (deviceFiles == null) {
            Toast.makeText(this@ItemListActivity,
                    R.string.no_usb_permission,
                    Toast.LENGTH_LONG).show()
            return
        }
        storageDevicesAdaper = StorageDevicesAdaper(this@ItemListActivity, deviceFiles)
        recyclerView.adapter = storageDevicesAdaper
        val layoutManager = LinearLayoutManager(this@ItemListActivity,
                LinearLayoutManager.VERTICAL, false)
        // Optionally customize the position you want to default scroll to
        layoutManager.scrollToPosition(0)
        recyclerView.layoutManager = layoutManager// Attach layout manager to the RecyclerView
        recyclerView.setHasFixedSize(true)
        dialogBuilder.setPositiveButton(R.string.OK) { dialogInterface, i ->
            if (storageDevicesAdaper!!.selectedDeviceRootPath != null) {
                if (storageDevicesAdaper!!.selectedDeviceRootPath!!.type == ConstantManager.DEFAULT_FILE) {
                    if (copyDBtoStorage(storageDevicesAdaper!!.selectedDeviceRootPath!!.defaultFile)) {
                        (application as MyApplication).toast(getString(R.string.backup_successful) +
                                " " + storageDevicesAdaper!!.selectedDeviceRootPath!!.deviceName)
                    } else {
                        (application as MyApplication).toast(getString(R.string.backup_failed))
                    }
                } else {
                    if (copyDBtoStorage(storageDevicesAdaper!!.selectedDeviceRootPath!!.usbFile)) {
                        (application as MyApplication).toast(getString(R.string.backup_successful) +
                                " " + storageDevicesAdaper!!.selectedDeviceRootPath!!.deviceName)
                    } else {
                        (application as MyApplication).toast(getString(R.string.backup_failed))
                    }
                }

            } else {
                (application as MyApplication).toast(getString(R.string.select_at_least_one_item))
            }
        }
        b = dialogBuilder.create()
        b.show()

    }

    /*
    This is a dialog used for backup database
     */
    fun restoreDialog() {
        val dialogBuilder = AlertDialog.Builder(this@ItemListActivity)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_backup_layout, null)
        dialogBuilder.setView(dialogView)
        dialogBuilder.setTitle(R.string.select_restore_position)
        dialogBuilder.setMessage(R.string.restore_warning)
        dialogBuilder.setNegativeButton(R.string.Cancel) { dialog, which -> }
        val b: AlertDialog
        val textView = dialogView.findViewById(R.id.backup_dialog_message) as TextView
        val recyclerView = dialogView.findViewById(R.id.recycle_view_storage_devices_list) as RecyclerView
        val deviceFiles = USBUtil.getDevicePathSet(this@ItemListActivity)
        if (deviceFiles == null) {
            Toast.makeText(this@ItemListActivity,
                    R.string.grant_permission_warning,
                    Toast.LENGTH_LONG).show()
            return
        }
        storageDevicesAdaper = StorageDevicesAdaper(this@ItemListActivity, deviceFiles)
        recyclerView.adapter = storageDevicesAdaper
        val layoutManager = LinearLayoutManager(this@ItemListActivity, LinearLayoutManager.VERTICAL, false)
        // Optionally customize the position you want to default scroll to
        layoutManager.scrollToPosition(0)
        recyclerView.layoutManager = layoutManager// Attach layout manager to the RecyclerView
        recyclerView.setHasFixedSize(true)
        dialogBuilder.setPositiveButton(R.string.OK) { dialog, which ->
            if (storageDevicesAdaper!!.selectedDeviceRootPath != null) {
                if (storageDevicesAdaper!!.selectedDeviceRootPath!!.type == ConstantManager.DEFAULT_FILE) {
                    if (copyDBtoAPP(storageDevicesAdaper!!.selectedDeviceRootPath!!.defaultFile,
                            textView)) {
                        (application as MyApplication).toast(getString(R.string.restore_successful))
                        initUI()
                    } else {
                        (application as MyApplication).toast(getString(R.string.restore_failed))
                    }
                } else {
                    if (copyDBtoAPP(storageDevicesAdaper!!.selectedDeviceRootPath!!.usbFile,
                            textView,
                            storageDevicesAdaper!!.selectedDeviceRootPath!!.device)) {
                        (application as MyApplication).toast(getString(R.string.restore_successful))
                        initUI()
                    } else {
                        (application as MyApplication).toast(getString(R.string.restore_failed))
                    }
                }

            } else {
                (application as MyApplication).toast(getString(R.string.select_at_least_one_item))
            }
        }
        b = dialogBuilder.create()
        b.show()

    }

    fun copyDBtoStorage(targetRoot: UsbFile): Boolean {
        try {
            val currentDB = getDatabasePath(getString(R.string.database_name))

            val backupDBName = String.format("%s.bak", getString(R.string.database_name))
            var exist = false
            var srcFile: UsbFile? = null
            for (file in targetRoot.listFiles()) {
                if (file.name == backupDBName) {
                    srcFile = file
                    exist = true
                }
            }
            if (exist)
                srcFile!!.delete()

            // write to a file
            val os = UsbFileOutputStream(targetRoot.createFile(backupDBName))
            os.write(getByteFromFile(currentDB))
            os.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    fun copyDBtoAPP(srcRoot: UsbFile, textView: TextView, device: UsbMassStorageDevice): Boolean {
        try {
            val backupDBPath = String.format("%s.bak", getString(R.string.database_name))
            val backupDB = getDatabasePath(getString(R.string.database_name))
            var exist = false
            var srcFile: UsbFile? = null
            for (file in srcRoot.listFiles()) {
                if (file.name == backupDBPath) {
                    srcFile = file
                    exist = true
                }
            }

            if (!exist) {
                textView.setText(R.string.no_backup_File_TF)
                return false
            }
            val param = CopyTaskParam()
            param.from = srcFile
            param.to = backupDB
            //            new CopyTask(device.getPartitions().get(0).getFileSystem()).execute(param);
            //

            val out = BufferedOutputStream(FileOutputStream(param.to!!))
            val inputStream = UsbFileStreamFactory.createBufferedInputStream(
                    param.from!!, device.partitions[0].fileSystem)
            val bytes = ByteArray(4096)
            var count: Int
            count = inputStream.read(bytes)
            while (count != -1) {
                out.write(bytes, 0, count)
                count = inputStream.read(bytes)
            }

            out.close()
            inputStream.close()


            return true
        } catch (e: Exception) {
            e.printStackTrace()
            textView.text = e.message
            return false
        }

    }

    fun copyDBtoStorage(targetpath: String): Boolean {
        try {
            val currentDB = getDatabasePath(getString(R.string.database_name))

            val backupDBPath = String.format("%s.bak", getString(R.string.database_name))
            val backupDB = File(targetpath, backupDBPath)
            if (!backupDB.exists()) {
                if (!backupDB.createNewFile())
                    return false
            }
            val src = FileInputStream(currentDB).channel
            val dst = FileOutputStream(backupDB).channel
            dst.transferFrom(src, 0, src.size())
            src.close()
            dst.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    fun copyDBtoAPP(srcPath: String, textView: TextView): Boolean {
        try {
            val backupDBPath = String.format("%s.bak", getString(R.string.database_name))
            val backupDB = getDatabasePath(getString(R.string.database_name))
            val currentDB = File(srcPath, backupDBPath)

            if (!backupDB.exists()) {
                textView.setText(R.string.no_backup_File_TF)
                return false
            }

            val src = FileInputStream(currentDB).channel
            val dst = FileOutputStream(backupDB).channel
            dst.transferFrom(src, 0, src.size())
            src.close()
            dst.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    private fun exit() {
        val builder = AlertDialog.Builder(this@ItemListActivity)
        builder.setMessage(R.string.exit_warning)
        builder.setPositiveButton(R.string.OK) { dialog, which -> finish() }
        builder.setNegativeButton(R.string.Cancel) { dialog, which -> }
        builder.create().show()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            exit()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.itemlist_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_bar_add -> alertDialog!!.show()
            R.id.checkout -> {
                // Goto Check out Page
//                intent = Intent(this@ItemListActivity, CheckoutActivity::class.java)
//                intent.putExtra(ConstantManager.CURRENT_USER_NAME, currentUser)
//                startActivity(intent)
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun getByteFromFile(file: File): ByteArray {
        //init array with file length
        val bytesArray = ByteArray(file.length().toInt())

        val fis = FileInputStream(file)
        fis.read(bytesArray) //read file into bytes[]
        fis.close()

        return bytesArray
    }

    /**
     * Class to hold the files for a copy task. Holds the source and the
     * destination file.

     * @author mjahnen
     */
    private class CopyTaskParam {
        /* package */ internal var from: UsbFile? = null
        /* package */ internal var to: File? = null
    }

    /**
     * Asynchronous task to copy a file from the mass storage device connected
     * via USB to the internal storage.

     * @author mjahnen
     */
    private inner class CopyTask(private val currentFs: FileSystem)//            dialog = new ProgressDialog(ItemListActivity.this);
    //            dialog.setTitle("Copying file");
    //            dialog.setMessage("Copying a file to the internal storage, this can take some time!");
    //            dialog.setIndeterminate(false);
    //            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        : AsyncTask<CopyTaskParam, Int, Void>() {

        //        private ProgressDialog dialog;
        private var param: CopyTaskParam? = null

        override fun onPreExecute() {
            //            dialog.show();
        }

        override fun doInBackground(vararg params: CopyTaskParam): Void? {
            param = params[0]
            try {
                val out = BufferedOutputStream(FileOutputStream(param!!.to!!))
                val inputStream = UsbFileStreamFactory.createBufferedInputStream(param!!.from!!, currentFs)
                val bytes = ByteArray(4096)
                var count: Int
                count = inputStream.read(bytes)

                while (count != -1) {
                    out.write(bytes, 0, count)
                }

                out.close()
                inputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return null
        }

        override fun onPostExecute(result: Void) {
            //            dialog.dismiss();

        }

        protected override fun onProgressUpdate(vararg values: Int?) {
            //            dialog.setMax((int) param.from.getLength());
            //            dialog.setProgress(values[0]);
        }

    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            val action = intent.action
            if (ConstantManager.ACTION_USB_PERMISSION == action) {

                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        discoverDevice()
                    }
                } else {
                    val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
                    val permissionIntent = PendingIntent.getBroadcast(this@ItemListActivity, 0, Intent(
                            ConstantManager.ACTION_USB_PERMISSION), 0)
                    usbManager.requestPermission(device, permissionIntent)
                }

            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                // determine if connected device is a mass storage devuce
                if (device != null) {
                    discoverDevice()
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)


                // determine if connected device is a mass storage devuce
                if (device != null) {
                    // check if there are other devices or set action bar title
                    // to no device if not
                    discoverDevice()
                }
            }

        }
    }

    /**
     * Refresh the list.
     */
    private fun discoverDevice() {
        if (storageDevicesAdaper != null) {
            storageDevicesAdaper!!.updateDataSet()
        }
    }

    // Blow is the RFID read part

    /**
     * start scan RFID cards and show at the dialog
     */
    private fun startScan() {
        OpenDev()
        openRF()
    }

    /**
     * stop scan RFID cards and stop at the dialog
     */
    private fun stopScan() {
        // 如果盘点标签线程正在运行，则关闭该线程
        // If thread of inventory is running,stop the thread before exit the
        // application.
        if (m_inventoryThrd != null && m_inventoryThrd!!.isAlive()) {
            b_inventoryThreadRun = false
            m_reader.RDR_SetCommuImmeTimeout()
            try {
                m_inventoryThrd!!.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        }
        closeRF()
        CloseDev()
        b_inventoryThreadRun = false
    }

    /**
     * Open the device
     */
    private fun OpenDev() {
        var conStr = ""
        val commTypeStr: String
        var devName = ""
        devName = "TPAD"
        commTypeStr = "USB"

        if (commTypeStr == "USB") {
            // 注意：使用USB方式时，必须先要枚举所有USB设备
            // Note: Before using USB, you must enumerate all USB devices first.
            val usbCnt = ADReaderInterface.EnumerateUsb(this)
            if (usbCnt <= 0) {
                Toast.makeText(this, getString(R.string.tx_msg_noUsb),
                        Toast.LENGTH_SHORT).show()
                return
            }

            if (!ADReaderInterface.HasUsbPermission("")) {
                Toast.makeText(this,
                        getString(R.string.tx_msg_noUsbPermission),
                        Toast.LENGTH_SHORT).show()
                ADReaderInterface.RequestUsbPermission("")
                return
            }

            conStr = String.format("RDType=%s;CommType=USB;Description=",
                    devName)
        } else {
            return
        }
        if (m_reader.RDR_Open(conStr) == ApiErrDefinition.NO_ERROR) {
            // ///////////////////////只有RPAN设备支持扫描模式/////////////////////////////

            Toast.makeText(this, getString(R.string.tx_msg_openDev_ok),
                    Toast.LENGTH_SHORT).show()

//            m_getScanRecordThrd = Thread(GetScanRecordThrd())
//            m_getScanRecordThrd!!.start()
            m_inventoryThrd = Thread(InventoryThrd())
            m_inventoryThrd!!.start()

        } else {
            Toast.makeText(this, getString(R.string.tx_msg_openDev_fail),
                    Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * close the device
     */
    private fun CloseDev() {
        if (m_getScanRecordThrd != null && m_getScanRecordThrd!!.isAlive()) {
            toast(getString(R.string.tx_msg_stopScanf_tip))
            return
        }
        m_reader.RDR_Close()
    }

    /**
     * open the RF
     */
    fun openRF() {
        var str = ""
        if (m_reader.RDR_OpenRFTransmitter() == ApiErrDefinition.NO_ERROR) {
            str = getString(R.string.tx_openRF_ok)
        } else {
            str = getString(R.string.tx_openRF_fail)
        }
        toast(getString(R.string.tx_openRF) + str)
    }

    /**
     * close the RF
     */
    fun closeRF() {
        var str = ""
        if (m_reader.RDR_CloseRFTransmitter() == ApiErrDefinition.NO_ERROR) {
            str = getString(R.string.tx_CloseRF_ok)// "关闭射频成功！";
        } else {
            str = getString(R.string.tx_CloseRF_fail)// "关闭射频失败";
        }
        toast(getString(R.string.tx_CloseRF) + str)
    }

    private var bGetScanRecordFlg = false


    private inner class GetScanRecordThrd : Runnable {
        override fun run() {
            var nret = 0
            bGetScanRecordFlg = true
            var gFlg: Byte = 0x00// 初次采集数据或者上一次采集数据失败时，标志位为0x00
            var dnhReport: Any? = null

            // 清空缓冲区记录
            nret = m_reader.RPAN_ClearScanRecord()
            if (nret != ApiErrDefinition.NO_ERROR) {
                bGetScanRecordFlg = false
                mHandler.sendEmptyMessage(THREAD_END)// 盘点结束
                return
            }

            while (bGetScanRecordFlg) {
                if (mHandler.hasMessages(GETSCANRECORD)) {
                    continue
                }
                nret = m_reader.RPAN_GetRecord(gFlg)
                if (nret != ApiErrDefinition.NO_ERROR) {
                    gFlg = 0x00
                    continue
                }
                gFlg = 0x01// 数据获取成功，将标志位设置为0x01
                dnhReport = m_reader
                        .RDR_GetTagDataReport(RfidDef.RFID_SEEK_FIRST)
                val dataList = Vector<String>()
                while (dnhReport != null) {
                    val byData = m_reader.RPAN_ParseRecord(dnhReport)
                    val strData = GFunction.encodeHexStr(byData)
                    dataList.add(strData)
                    dnhReport = m_reader
                            .RDR_GetTagDataReport(RfidDef.RFID_SEEK_NEXT)
                }
                if (!dataList.isEmpty()) {
                    val msg = mHandler.obtainMessage()
                    msg.what = GETSCANRECORD
                    msg.obj = dataList
                    mHandler.sendMessage(msg)
                }
            }
            bGetScanRecordFlg = false
            mHandler.sendEmptyMessage(THREAD_END)// 结束
        }
    };

    /**
     * Scan the Cards at another thread.
     */
    private var b_inventoryThreadRun = false

    private inner class InventoryThrd : Runnable {
        override fun run() {
            var failedCnt = 0// 操作失败次数
            var hInvenParamSpecList: Any? = null
            var newAI = RfidDef.AI_TYPE_NEW
            if (bOnlyReadNew) {
                newAI = RfidDef.AI_TYPE_CONTINUE
            }
            if (!bUseDefaultPara) {
                hInvenParamSpecList = ADReaderInterface
                        .RDR_CreateInvenParamSpecList()
                ISO15693Interface.ISO15693_CreateInvenParam(
                        hInvenParamSpecList, 0.toByte(), bMathAFI, mAFIVal,
                        0.toByte())
            }
            b_inventoryThreadRun = true
            while (b_inventoryThreadRun) {
                if (mHandler.hasMessages(INVENTORY_MSG)) {
                    continue
                }
                var iret = m_reader.RDR_TagInventory(newAI, null, 0,
                        hInvenParamSpecList)
                if (iret == ApiErrDefinition.NO_ERROR || iret == -ApiErrDefinition.ERR_STOPTRRIGOCUR) {
                    val tagList = Vector<ISO15693Tag>()
                    newAI = RfidDef.AI_TYPE_NEW
                    if (bOnlyReadNew || iret == -ApiErrDefinition.ERR_STOPTRRIGOCUR) {
                        newAI = RfidDef.AI_TYPE_CONTINUE
                    }

                    var tagReport: Any? = m_reader
                            .RDR_GetTagDataReport(RfidDef.RFID_SEEK_FIRST)
                    while (tagReport != null) {
                        val tagData = ISO15693Tag()
                        iret = ISO15693Interface.ISO15693_ParseTagDataReport(
                                tagReport, tagData)
                        if (iret == ApiErrDefinition.NO_ERROR) {
                            tagList.add(tagData)
                        }
                        tagReport = m_reader
                                .RDR_GetTagDataReport(RfidDef.RFID_SEEK_NEXT)
                    }
                    if (!tagList.isEmpty()) {
                        if (bBuzzer) {
                            this@ItemListActivity.playVoice()
                        }
                        val msg = mHandler.obtainMessage()
                        msg.what = INVENTORY_MSG
                        msg.obj = tagList
                        msg.arg1 = failedCnt
                        mHandler.sendMessage(msg)
                    }
                } else {
                    newAI = RfidDef.AI_TYPE_NEW
                    if (b_inventoryThreadRun) {
                        failedCnt++
                    }
                    val msg = mHandler.obtainMessage()
                    msg.what = INVENTORY_FAIL_MSG
                    msg.arg1 = failedCnt
                    mHandler.sendMessage(msg)
                }

                try {
                    Thread.sleep(30)
                } catch (e: InterruptedException) {
                }

            }
            b_inventoryThreadRun = false
            m_reader.RDR_ResetCommuImmeTimeout()
            mHandler.sendEmptyMessage(THREAD_END)// 盘点结束
        }
    };

    private val mHandler = MyHandler(this)

    private class MyHandler(activity: ItemListActivity) : Handler() {
        private val mActivity: WeakReference<ItemListActivity>

        init {
            mActivity = WeakReference(activity)
        }

        override fun handleMessage(msg: Message) {
            val itemListActivity = mActivity.get() ?: return
            when (msg.what) {
                itemListActivity.INVENTORY_MSG -> { // 盘点到标签
                    val tagList = msg.obj as Vector<ISO15693Tag>
                    for (i in tagList.indices) {
                        val tagData = tagList[i]// (ISO15693Tag)
                        // msg.obj;
                        val uidStr = GFunction.encodeHexStr(tagData.uid)
                        itemListActivity.addNewCardToList(uidStr)
                    }

                }
                itemListActivity.INVENTORY_FAIL_MSG -> Toast.makeText(itemListActivity,
                        "read failed", Toast.LENGTH_LONG).show()

                itemListActivity.THREAD_END -> null // 线程结束
                else -> {
                }
            }
        }
    }

    // 播放声音池声音
    private fun playVoice() {
        val am = this
                .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioCurrentVolume = am
                .getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        soundPool!!.play(soundID, // 播放的音乐Id
                audioCurrentVolume, // 左声道音量
                audioCurrentVolume, // 右声道音量
                1, // 优先级，0为最低
                0, // 循环次数，0无不循环，-1无永远循环
                1f)// 回放速度，值在0.5-2.0之间，1为正常速度
    }
}