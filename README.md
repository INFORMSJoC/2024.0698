[![INFORMS Journal on Computing Logo](https://INFORMSJoC.github.io/logos/INFORMS_Journal_on_Computing_Header.jpg)](https://pubsonline.informs.org/journal/ijoc)

# DDD for MDVSP-TS

This archive is distributed in association with the [INFORMS Journal on
Computing](https://pubsonline.informs.org/journal/ijoc) under the [MIT License](LICENSE).

The software and data in this repository are a snapshot of the software and data
that were used in the research reported on in the paper 
[Dynamic Discretization Discovery for the Multi-Depot Vehicle Scheduling Problem with Trip Shifting](https://doi.org/10.1287/ijoc.2024.0698) by R.N. van Lieshout and D.T.J. van der Schaft. 

**Important: This code is being developed on an on-going basis at 
https://github.com/rn-van-lieshout/DDD-MDVSP-TS. Please go there if you would like to
get a more recent version or would like support**

## Cite

To cite the contents of this repository, please cite both the paper and this repo, using their respective DOIs.

https://doi.org/10.1287/ijoc.2024.0698

https://doi.org/10.1287/ijoc.2024.0698.cd

Below is the BibTex for citing this snapshot of the repository.

```
@misc{CacheTest,
  author =        {Van Lieshout, Rolf N and Van der Schaft, Thomas},
  publisher =     {INFORMS Journal on Computing},
  title =         {{Dynamic Discretization Discovery for the Multi-Depot Vehicle Scheduling Problem with Trip Shifting}},
  year =          {2025},
  doi =           {10.1287/ijoc.2024.0698.cd},
  url =           {https://github.com/INFORMSJoC/2024.0698},
  note =          {Available for download at https://github.com/INFORMSJoC/2024.0698},
}  
```

## Description
 
This repository contains the code for solving the Multi-Depot Vehicle Scheduling Problem with Trip Shifting. Three algorithms are implemented: 

- MIP: a mixed-integer programming model based on the fully time-expanded network
- DDD: a Dynamic Discretization Discovery Algorithm that starts with a sparse network and adds time points dynamically when needed
- BNP: a Branch-and-Price algorithm based on an extended route formulation

The code depends on CPLEX to solve all LPs and MIPs. The experiments in the paper were all performed using CPLEX 22.1.

All data is in the folder "dataEUC", and the source code and scripts are in "src". There are three scripts in "src/scripts", one for each algorithm (MainMIP, MainDDD and MainBNP). Instance parameters can be directly set in these scripts. Algorithm parameters for DDD and BNP can be set in "src/ddd/Solver_DDD" and src/"bnp"/BranchAndPrice, respectively.
