/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.entities.livestock;

import java.util.function.Supplier;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.serialization.Dynamic;
import net.dries007.tfc.common.entities.ai.livestock.LivestockAi;
import net.dries007.tfc.client.TFCSounds;
import net.dries007.tfc.config.animals.AnimalConfig;
import net.dries007.tfc.util.calendar.Calendars;

public abstract class TFCAnimal extends Animal implements TFCAnimalProperties
{
    private static final EntityDataAccessor<Boolean> GENDER = SynchedEntityData.defineId(TFCAnimal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BIRTHDAY = SynchedEntityData.defineId(TFCAnimal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> FAMILIARITY = SynchedEntityData.defineId(TFCAnimal.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> USES = SynchedEntityData.defineId(TFCAnimal.class, EntityDataSerializers.INT);
    //Is this female fertilized? (in oviparous, the egg laying is fertilized, for mammals this is pregnancy)
    private static final EntityDataAccessor<Boolean> FERTILIZED = SynchedEntityData.defineId(TFCAnimal.class, EntityDataSerializers.BOOLEAN);

    private static final CommonAnimalData ANIMAL_DATA = new CommonAnimalData(GENDER, BIRTHDAY, FAMILIARITY, USES, FERTILIZED);

    private long lastFed; //Last time(in days) this entity was fed
    private long lastFDecay; //Last time(in days) this entity's familiarity had decayed
    private long matingTime; //The last time(in ticks) this male tried fertilizing females
    private final Supplier<? extends SoundEvent> ambient;
    private final Supplier<? extends SoundEvent> hurt;
    private final Supplier<? extends SoundEvent> death;
    private final Supplier<? extends SoundEvent> step;
    private final AnimalConfig config;

    public TFCAnimal(EntityType<? extends Animal> type, Level level, TFCSounds.EntitySound sounds, AnimalConfig config)
    {
        super(type, level);
        getNavigation().setCanFloat(true);
        this.matingTime = Calendars.get(level).getTicks();
        this.lastFDecay = Calendars.get(level).getTotalDays();
        this.ambient = sounds.ambient();
        this.hurt = sounds.hurt();
        this.death = sounds.death();
        this.step = sounds.step();
        this.config = config;
    }

    // Next four overrides are the entire package needed to make Brain work

    @Override
    protected Brain.Provider<? extends TFCAnimal> brainProvider()
    {
        return Brain.provider(LivestockAi.MEMORY_TYPES, LivestockAi.SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic)
    {
        return LivestockAi.makeBrain(brainProvider().makeBrain(dynamic));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Brain<? extends TFCAnimal> getBrain()
    {
        return (Brain<TFCAnimal>) super.getBrain();
    }

    @Override
    protected void customServerAiStep()
    {
        super.customServerAiStep();
        tickBrain();
    }

    @SuppressWarnings("unchecked")
    public void tickBrain()
    {
        ((Brain<TFCAnimal>) getBrain()).tick((ServerLevel) level, this);
        // updateActivity function would go here
    }

    @Override
    public CommonAnimalData animalData()
    {
        return ANIMAL_DATA;
    }

    @Override
    public AnimalConfig animalConfig()
    {
        return config;
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        registerCommonData();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt)
    {
        super.addAdditionalSaveData(nbt);
        saveCommonAnimalData(nbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt)
    {
        super.readAdditionalSaveData(nbt);
        readCommonAnimalData(nbt);
    }

    @Override
    public boolean isBaby()
    {
        return getAgeType() == Age.CHILD;
    }

    @Override
    public void setAge(int age)
    {
        super.setAge(0); // no-op vanilla aging
    }

    @Nullable
    @SuppressWarnings("unchecked")
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other)
    {
        // Cancel default vanilla behaviour (immediately spawns children of this animal) and set this female as fertilized
        if (other != this && this.getGender() == Gender.FEMALE && other instanceof TFCAnimalProperties otherFertile)
        {
            this.onFertilized(otherFertile);
        }
        else if (other == this)
        {
            TFCAnimal baby = ((EntityType<TFCAnimal>) getType()).create(level);
            if (baby != null)
            {
                baby.setGender(Gender.valueOf(random.nextBoolean()));
                baby.setBirthDay((int) Calendars.SERVER.getTotalDays());
                baby.setFamiliarity(this.getFamiliarity() < 0.9F ? this.getFamiliarity() / 2.0F : this.getFamiliarity() * 0.9F);
                return baby;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag tag)
    {
        if (reason != MobSpawnType.BREEDING)
        {
            initCommonAnimalData();
        }
        return super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data)
    {
        super.onSyncedDataUpdated(data);
        if (BIRTHDAY.equals(data))
        {
            refreshDimensions();
        }
    }

    @Override
    public long getLastFamiliarityDecay()
    {
        return lastFDecay;
    }

    @Override
    public void setLastFamiliarityDecay(long days)
    {
        lastFDecay = days;
    }

    @Override
    public void setLastFed(long fed)
    {
        lastFed = fed;
    }

    @Override
    public long getLastFed()
    {
        return lastFed;
    }

    @Override
    public void setMated(long ticks)
    {
        matingTime = ticks;
    }

    @Override
    public long getMated()
    {
        return matingTime;
    }

    @Override
    public void tick()
    {
        super.tick();
        tickFamiliarity();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        InteractionResult result = TFCAnimalProperties.super.mobInteract(player, hand);
        return result == InteractionResult.PASS ? super.mobInteract(player, hand) : result;
    }

    @Override
    public boolean canMate(Animal otherAnimal)
    {
        if (otherAnimal.getClass() != this.getClass()) return false;
        TFCAnimal other = (TFCAnimal) otherAnimal;
        return this.getGender() != other.getGender() && other.isReadyToMate();
    }

    @Override
    public boolean isFood(ItemStack stack)
    {
        return TFCAnimalProperties.super.isFood(stack);
    }

    @Override
    public Component getTypeName()
    {
        return TFCAnimalProperties.super.getTypeName();
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ambient.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src)
    {
        return hurt.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return death.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block)
    {
        this.playSound(step.get(), 0.15F, 1.0F);
    }
}
