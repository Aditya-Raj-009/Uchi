

package com.avi.gharkhojo.Fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.avi.gharkhojo.LoginActivity
import com.avi.gharkhojo.Model.ChatUserListModel
import com.avi.gharkhojo.Model.UserData
import com.avi.gharkhojo.Model.UserDetails
import com.avi.gharkhojo.Model.UserSignupLoginManager
import com.avi.gharkhojo.R
import com.avi.gharkhojo.databinding.FragmentProfileBinding
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.ismaeldivita.chipnavigation.ChipNavigationBar
import com.yalantis.ucrop.UCrop
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.*
import javax.inject.Inject


class Profile : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
      var firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    var UserCollection:CollectionReference = FirebaseFirestore.getInstance().collection("users")

     var firebaseUser:FirebaseUser? = firebaseAuth.currentUser
    private lateinit var pickImage: ActivityResultLauncher<String>
    private lateinit var cropImage: ActivityResultLauncher<Intent>
    var databaseReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("users")
    private val storageRef:StorageReference by lazy { Firebase.storage.reference.child("profile_pictures/${FirebaseAuth.getInstance().currentUser?.uid}") }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var otherId = arguments?.getString("uid")
        val bottomNav = activity?.findViewById<ChipNavigationBar>(R.id.bottom_nav_bar)
        if((!otherId.isNullOrEmpty()) && otherId != firebaseUser?.uid){
            loadOtherUserProfile(otherId)
            binding.fabEditProfile.visibility = View.GONE
            binding.buttonSignOut.visibility = View.GONE
            bottomNav?.visibility = View.GONE
            return
        }else{
            binding.fabEditProfile.visibility = View.VISIBLE
            binding.buttonSignOut.visibility = View.VISIBLE
            bottomNav?.visibility = View.VISIBLE
            bottomNav?.setItemSelected(R.id.nav_profile, true)
        }

        loadUserData()
        loadProfileImage()
        setupClickListeners()

        initImagePicker()

    }

    private fun loadOtherUserProfile(otherId: String) {

        databaseReference.addListenerForSingleValueEvent(object: ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                for(dataSnapshot in snapshot.children){
                    val userData = dataSnapshot.getValue(ChatUserListModel::class.java)
                    if(userData?.userId == otherId){
                        binding.textViewUsername.text = userData.username
                        binding.textViewEmail.text = userData.userEmail
                        Glide.with(this@Profile)
                            .load(userData.userimage)
                            .placeholder(R.drawable.india)
                            .error(R.drawable.background2)
                            .centerCrop()

                      UserCollection.document(otherId).get().addOnSuccessListener {
                            if(it.exists()) {
                                val userDetails = it.toObject(UserDetails::class.java)
                                binding.textViewPhone.text = userDetails?.phn_no
                                binding.textRoadNo.text = userDetails?.Road_Lane
                                binding.textViewCity.text = userDetails?.City
                                binding.textViewState.text = userDetails?.State
                                binding.textViewPincode.text = userDetails?.Pincode
                                binding.textViewArea.text = userDetails?.Area
                                binding.textViewLandmark.text = userDetails?.LandMark
                                binding.textViewHouseNo.text = userDetails?.HouseNo
                                binding.textViewColony.text = userDetails?.colony
                            }
                        }

                        break
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }

        })
    }

    private fun loadProfileImage() {

        Glide.with(this)
            .load(UserData.profilePictureUrl ?: R.drawable.india)
            .placeholder(R.drawable.india)
            .error(R.drawable.background2)
            .centerCrop()
            .into(binding.ProfilePic)
    }

    private fun setupClickListeners() {
        binding.ProfilePic.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.buttonSignOut.setOnClickListener {
            signOut()
        }

        binding.fabEditProfile.setOnClickListener {
            showProfileEditBottomSheet()
        }
    }


    private fun loadUserData() {

        binding.textViewUsername.text = UserData.username ?: ""
        binding.textViewEmail.text = firebaseUser?.email ?: ""

        binding.textViewPhone.text = UserData.phn_no ?: ""
        binding.textRoadNo.text = UserData.Road_Lane ?: ""
        binding.textViewCity.text = UserData.City ?: ""
        binding.textViewState.text = UserData.State ?: ""
        binding.textViewPincode.text = UserData.Pincode ?: ""
        binding.textViewArea.text = UserData.Area ?: ""
        binding.textViewLandmark.text = UserData.LandMark ?: ""
        binding.textViewHouseNo.text = UserData.HouseNo ?: ""
        binding.textViewColony.text = UserData.colony ?: ""


    }



    private fun initImagePicker() {
        pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { startCrop(it) }
        }

        cropImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let {
                    val resultUri = UCrop.getOutput(it)
                    resultUri?.let { uri ->
                        UserData.profilePictureUrl = uri.toString()
                        storageRef.putFile(uri).addOnSuccessListener {
                            storageRef.downloadUrl.addOnSuccessListener {
                                UserData.profilePictureUrl = it.toString()

                                databaseReference.addValueEventListener(object:ValueEventListener{
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        for(dataSnapshot in snapshot.children){
                                            val userData = dataSnapshot.getValue(ChatUserListModel::class.java)
                                            if(userData?.userId.equals(FirebaseAuth.getInstance().currentUser?.uid)){
                                                databaseReference.child(dataSnapshot.key.toString()).child("userimage").setValue(UserData.profilePictureUrl)
                                            }
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError) {

                                    }

                                })
                            }

                        }
                        Glide.with(this)
                            .load(uri)
                            .placeholder(R.drawable.india)
                            .error(R.drawable.background2)
                            .centerCrop()
                            .into(binding.ProfilePic)
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!)
                Toast.makeText(requireContext(), cropError?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCrop(uri: Uri) {
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, "croppedImage_${UUID.randomUUID()}.jpg"))
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setHideBottomControls(true)
            setShowCropGrid(false)
            setCropFrameColor(ContextCompat.getColor(requireContext(), R.color.black))
        }
        val uCrop = UCrop.of(uri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withOptions(options)

        cropImage.launch(uCrop.getIntent(requireContext()))
    }

    private fun signOut() {
        firebaseAuth.signOut()
        UserData.clear()
        UserSignupLoginManager.instance = null

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
        googleSignInClient.signOut().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(requireContext(), getString(R.string.sign_out_success), Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireActivity(), LoginActivity::class.java))
                requireActivity().finish()
            } else {
                Toast.makeText(requireContext(), getString(R.string.sign_out_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProfileEditBottomSheet() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            val profileBottomSheet = ProfileBottomSheet()
            profileBottomSheet.profileBinding = binding
            profileBottomSheet.show(childFragmentManager, ProfileBottomSheet::class.java.simpleName)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        var bottomNav = activity?.findViewById<ChipNavigationBar>(R.id.bottom_nav_bar)
        bottomNav?.visibility = View.VISIBLE
    }
}
