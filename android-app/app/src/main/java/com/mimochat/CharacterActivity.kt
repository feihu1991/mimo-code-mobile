package com.mimochat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CharacterActivity : AppCompatActivity() {
    
    private lateinit var characterRecyclerView: RecyclerView
    private lateinit var characterAdapter: CharacterAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character)
        
        initViews()
        setupRecyclerView()
    }
    
    private fun initViews() {
        characterRecyclerView = findViewById(R.id.characterRecyclerView)
        title = "选择角色"
    }
    
    private fun setupRecyclerView() {
        val characters = CharacterManager.getAllCharacters()
        val selectedCharacter = CharacterManager.getSelectedCharacter(this)
        
        characterAdapter = CharacterAdapter(
            characters = characters,
            selectedId = selectedCharacter.id,
            onCharacterSelected = { character ->
                CharacterManager.setSelectedCharacter(this, character)
                finish()
            }
        )
        
        characterRecyclerView.layoutManager = LinearLayoutManager(this)
        characterRecyclerView.adapter = characterAdapter
    }
}

class CharacterAdapter(
    private val characters: List<Character>,
    private val selectedId: String,
    private val onCharacterSelected: (Character) -> Unit
) : RecyclerView.Adapter<CharacterAdapter.CharacterViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_character, parent, false)
        return CharacterViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        val character = characters[position]
        holder.bind(character, character.id == selectedId)
        holder.itemView.setOnClickListener {
            onCharacterSelected(character)
        }
    }
    
    override fun getItemCount(): Int = characters.size
    
    class CharacterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarText: TextView = itemView.findViewById(R.id.avatarText)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val selectedIndicator: View = itemView.findViewById(R.id.selectedIndicator)
        
        fun bind(character: Character, isSelected: Boolean) {
            avatarText.text = character.avatar
            nameText.text = character.name
            descriptionText.text = character.description
            selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }
}
